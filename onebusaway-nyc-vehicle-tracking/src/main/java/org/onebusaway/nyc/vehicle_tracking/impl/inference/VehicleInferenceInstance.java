/**
 * Copyright (c) 2011 Metropolitan Transportation Authority
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.onebusaway.nyc.vehicle_tracking.impl.inference;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.builder.StandardToStringStyle;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import org.onebusaway.geospatial.model.CoordinatePoint;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.nyc.transit_data.model.NycQueuedInferredLocationBean;
import org.onebusaway.nyc.transit_data.model.NycVehicleManagementStatusBean;
import org.onebusaway.nyc.transit_data_federation.bundle.tasks.stif.model.RunTripEntry;
import org.onebusaway.nyc.transit_data_federation.model.tdm.OperatorAssignmentItem;
import org.onebusaway.nyc.transit_data_federation.services.nyc.BaseLocationService;
import org.onebusaway.nyc.transit_data_federation.services.nyc.DestinationSignCodeService;
import org.onebusaway.nyc.transit_data_federation.services.nyc.RunService;
import org.onebusaway.nyc.transit_data_federation.services.tdm.OperatorAssignmentService;
import org.onebusaway.nyc.util.configuration.ConfigurationService;
import org.onebusaway.nyc.vehicle_tracking.impl.inference.state.BlockState;
import org.onebusaway.nyc.vehicle_tracking.impl.inference.state.BlockStateObservation;
import org.onebusaway.nyc.vehicle_tracking.impl.inference.state.JourneyState;
import org.onebusaway.nyc.vehicle_tracking.impl.inference.state.MotionState;
import org.onebusaway.nyc.vehicle_tracking.impl.inference.state.MtaPathStateBelief;
import org.onebusaway.nyc.vehicle_tracking.impl.particlefilter.ParticleFilterModel;
import org.onebusaway.nyc.vehicle_tracking.model.NycRawLocationRecord;
import org.onebusaway.nyc.vehicle_tracking.model.NycTestInferredLocationRecord;
import org.onebusaway.nyc.vehicle_tracking.model.library.RecordLibrary;
import org.onebusaway.nyc.vehicle_tracking.model.simulator.VehicleLocationDetails;
import org.onebusaway.nyc.vehicle_tracking.opentrackingtools.impl.MtaTrackingGraph;
import org.onebusaway.nyc.vehicle_tracking.opentrackingtools.impl.MtaVehicleState;
import org.onebusaway.nyc.vehicle_tracking.opentrackingtools.impl.MtaVehicleTrackingPLFilter;
import org.onebusaway.nyc.vehicle_tracking.opentrackingtools.impl.RunState;
import org.onebusaway.nyc.vehicle_tracking.opentrackingtools.impl.RunStateEstimator;
import org.onebusaway.realtime.api.EVehiclePhase;
import org.onebusaway.transit_data_federation.services.AgencyAndIdLibrary;
import org.onebusaway.transit_data_federation.services.blocks.BlockInstance;
import org.onebusaway.transit_data_federation.services.blocks.ScheduledBlockLocation;
import org.onebusaway.transit_data_federation.services.transit_graph.BlockConfigurationEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.BlockEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.BlockTripEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.TripEntry;

import gov.sandia.cognition.math.matrix.VectorFactory;
import gov.sandia.cognition.statistics.DataDistribution;
import gov.sandia.cognition.statistics.distribution.DefaultDataDistribution;
import gov.sandia.cognition.statistics.distribution.MultivariateGaussian;

import org.opengis.referencing.operation.NoninvertibleTransformException;
import org.opengis.referencing.operation.TransformException;
import org.opentrackingtools.graph.paths.edges.PathEdge;
import org.opentrackingtools.graph.paths.states.PathStateBelief;
import org.opentrackingtools.impl.VehicleState;
import org.opentrackingtools.impl.VehicleStateInitialParameters;
import org.opentrackingtools.statistics.distributions.impl.DeterministicDataDistribution;
import org.opentrackingtools.statistics.filters.vehicles.particle_learning.impl.VehicleTrackingPLFilter;
import org.opentrackingtools.statistics.filters.vehicles.road.impl.ErrorEstimatingRoadTrackingFilter;
import org.opentrackingtools.statistics.filters.vehicles.road.impl.ForwardMovingRoadTrackingFilter;
import org.opentrackingtools.util.GeoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.base.Predicate;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import com.google.common.collect.TreeMultiset;
import com.vividsolutions.jts.geom.Coordinate;

public class VehicleInferenceInstance {

  private static Logger _log = LoggerFactory.getLogger(VehicleInferenceInstance.class);

  static {
    ToStringBuilder.setDefaultStyle(ToStringStyle.SHORT_PREFIX_STYLE);
  }
  
  @Autowired
  private ConfigurationService _configurationService;
  
  @Autowired
  private MtaTrackingGraph _trackingGraph;
  
  private VehicleStateInitialParameters _initialParams =
      new VehicleStateInitialParameters(
          VectorFactory.getDefault().createVector2D(100d, 100d), 20,
          VectorFactory.getDefault().createVector1D(0.00625), 20,
          VectorFactory.getDefault().createVector2D(0.00625, 0.00625), 20,
          VectorFactory.getDefault().createVector2D(5d, 95d),
          VectorFactory.getDefault().createVector2D(95d, 5d), 
          VehicleTrackingPLFilter.class.getName(),
          ForwardMovingRoadTrackingFilter.class.getName(),
//          ErrorEstimatingRoadTrackingFilter.class.getName(),
          25, 30, 0l);
  
  private DestinationSignCodeService _destinationSignCodeService;

  private BaseLocationService _baseLocationService;

  private OperatorAssignmentService _operatorAssignmentService;

  private RunService _runService;

  private long _automaticResetWindow = 20 * 60 * 1000;

  private Observation _previousObservation = null;

  private String _lastValidDestinationSignCode = null;

  private long _lastUpdateTime = 0;

  private long _lastLocationUpdateTime = 0;

  private NycTestInferredLocationRecord _nycTestInferredLocationRecord;

  private DataDistribution<VehicleState> _particles;
  
  private VehicleTrackingPLFilter _particleFilter;

  private MtaVehicleState prevAvgVehicleState;

  private MtaVehicleState currentAvgVehicleState;

  public void setModel(ParticleFilterModel<Observation> model) {
    // TODO remove.
  }

  @Autowired
  public void setRunService(RunService runService) {
    _runService = runService;
  }

  @Autowired
  public void setOperatorAssignmentService(OperatorAssignmentService operatorAssignmentService) {
    _operatorAssignmentService = operatorAssignmentService;
  }

  public OperatorAssignmentService getOperatorAssignmentService() {
    return _operatorAssignmentService;
  }

  @Autowired
  public void setDestinationSignCodeService(DestinationSignCodeService destinationSignCodeService) {
    _destinationSignCodeService = destinationSignCodeService;
  }

  @Autowired
  public void setBaseLocationService(BaseLocationService baseLocationService) {
    _baseLocationService = baseLocationService;
  }

  /**
   * If we haven't received a GPS update in the specified window, the inference
   * engine is reset no matter what
   * 
   * @param automaticResetWindow
   */
  public void setAutomaticResetWindow(long automaticResetWindow) {
    _automaticResetWindow = automaticResetWindow;
  }

  /**
   * Process a NycRawLocationRecord, usually from a bus or the simulator.
   * 
   * @param record
   * @return true if the resulting inferred location record was successfully processed,
   *         otherwise false
   * @throws TransformException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   * @throws InstantiationException 
   * @throws NoSuchMethodException 
   * @throws ClassNotFoundException 
   * @throws IllegalArgumentException 
   * @throws SecurityException 
   */
  public boolean handleUpdate(NycRawLocationRecord record) throws TransformException, SecurityException, IllegalArgumentException, ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {

    /**
     * Choose the best timestamp based on device timestamp and received
     * timestamp
     */
    final long timestamp = RecordLibrary.getBestTimestamp(record.getTime(),record.getTimeReceived());
    _lastUpdateTime = timestamp;

    /**
     * If this record occurs BEFORE, or at the same time as, the most recent update, 
     * we take special action
     */
    if (_particleFilter != null && 
        _particleFilter.getLastProcessedTime() != null && 
        timestamp <= _particleFilter.getLastProcessedTime()) {
      final long backInTime = (long) (_particleFilter.getLastProcessedTime() - timestamp);

      /**
       * If the difference is large, we reset the particle filter. Otherwise, we
       * simply ignore the out-of-order record
       */
      if (backInTime > 5 * 60 * 1000) {
        _log.info("resetting filter: time diff");
        _previousObservation = null;
        _particleFilter = null;
      } else {
        _log.info("out-of-order record.  skipping update.");
        return false;
      }
    }

    /**
     * If it's been a while since we've last seen a record, reset the particle
     * filter and forget the previous observation
     */
    Boolean reportedRunIdChange = null;
    Boolean operatorIdChange = null;

    if (_previousObservation != null) {
      /**
       * We use an absolute value here, since we also want to reset if we go
       * back in time as well
       */
      final long delta = Math.abs(timestamp - _previousObservation.getTime());
      final NycRawLocationRecord lastRecord = _previousObservation.getRecord();

      reportedRunIdChange = !StringUtils.equals(lastRecord.getRunId(), record.getRunId());
      operatorIdChange = !StringUtils.equals(lastRecord.getOperatorId(), record.getOperatorId());

      if (delta > _automaticResetWindow) {
        _log.info("resetting inference for vid=" + record.getVehicleId()
            + " since it's been " + (delta / 1000)
            + " seconds since the previous update");
        _previousObservation = null;
        if (_particleFilter != null) {
          _particleFilter = null;
        }
      }
    }

    /**
     * Recall that a vehicle might send a location update with missing lat-lon
     * if it's sitting at the curb with the engine turned off.
     */
    final boolean latlonMissing = record.locationDataIsMissing();
    if (latlonMissing) {
      /**
       * If we don't have a previous record, we can't use the previous lat-lon
       * to replace the missing values
       */
      if (_previousObservation == null) {
        _log.info("missing previous observation and current lat/lon:"
            + record.getVehicleId() + ", skipping update.");
        return false;
      }

      final NycRawLocationRecord previousRecord = _previousObservation.getRecord();
      record.setLatitude(previousRecord.getLatitude());
      record.setLongitude(previousRecord.getLongitude());
    }

    final CoordinatePoint location = new CoordinatePoint(record.getLatitude(), record.getLongitude());

    /**
     * Sometimes, DSCs take the form "  11", where there is whitespace in there.
     * Let's clean it up.
     */
    String dsc = record.getDestinationSignCode();
    if (dsc != null) {
      dsc = dsc.trim();
      record.setDestinationSignCode(dsc);
    }

    String lastValidDestinationSignCode = null;

    if (dsc != null && !_destinationSignCodeService.isMissingDestinationSignCode(dsc)) {
      lastValidDestinationSignCode = dsc;
    } else if (_previousObservation != null) {
      lastValidDestinationSignCode = _lastValidDestinationSignCode;
    }

    final boolean atBase = _baseLocationService.getBaseNameForLocation(location) != null;
    final boolean atTerminal = false;
    final boolean outOfService = _destinationSignCodeService.isOutOfServiceDestinationSignCode(lastValidDestinationSignCode);
    final boolean hasValidDsc = !_destinationSignCodeService.isMissingDestinationSignCode(lastValidDestinationSignCode)
        && !_destinationSignCodeService.isUnknownDestinationSignCode(lastValidDestinationSignCode);

    Set<AgencyAndId> routeIds = new HashSet<AgencyAndId>();
    if (_previousObservation == null || !StringUtils.equals(_lastValidDestinationSignCode, lastValidDestinationSignCode)) {
      routeIds = _destinationSignCodeService.getRouteCollectionIdsForDestinationSignCode(lastValidDestinationSignCode);
    } else {
      routeIds = _previousObservation.getDscImpliedRouteCollections();
    }

    RunResults runResults = null;
    if (_previousObservation == null || operatorIdChange == Boolean.TRUE || reportedRunIdChange == Boolean.TRUE) {
      runResults = findRunIdMatches(record);
    } else {
      runResults = updateRunIdMatches(record, _previousObservation.getRunResults());
    }

    final Observation observation = new Observation(timestamp, record,
        lastValidDestinationSignCode, atBase, atTerminal, outOfService,
        hasValidDsc, _previousObservation, routeIds, runResults);

    if (_previousObservation != null)
      _previousObservation.clearPreviousObservation();

    _previousObservation = observation;
    _nycTestInferredLocationRecord = null;
    _lastValidDestinationSignCode = lastValidDestinationSignCode;

    if (!latlonMissing)
      _lastLocationUpdateTime = timestamp;

    if (_particleFilter == null) {
      Random rng = new Random();
      _particleFilter = new MtaVehicleTrackingPLFilter(observation, 
          _trackingGraph, _initialParams, true, rng);
      _trackingGraph.setRng(rng);
    }
    if (_particles == null || _particles.isEmpty()) {
      _particles = _particleFilter.createInitialLearnedObject();
    } else {
      _particleFilter.update(_particles, observation);
    }

    return true;
  }

  /**
   * Pass a previous inference result as if it was ours, from the simulator.
   * 
   * @param record
   * @return true if the resulting inferred location record should be passed on,
   *         otherwise false.
   */
  public synchronized boolean handleBypassUpdate(
      NycTestInferredLocationRecord record) {
    _previousObservation = null;
    _nycTestInferredLocationRecord = record;
    _lastUpdateTime = record.getTimestamp();

    if (!record.locationDataIsMissing())
      _lastLocationUpdateTime = record.getTimestamp();

    return true;
  }

  public synchronized DataDistribution<VehicleState> getCurrentParticles() {
    return _particles;
  }

  public NycTestInferredLocationRecord getCurrentState() {
    if (_previousObservation != null)
      return getMostRecentParticleAsNycTestInferredLocationRecord();
    else if (_nycTestInferredLocationRecord != null)
      return _nycTestInferredLocationRecord;
    else
      return null;
  }

  public VehicleTrackingPLFilter getFilter() {
    return _particleFilter;
  }

  public VehicleLocationDetails getDetails() {
    final VehicleLocationDetails details = new VehicleLocationDetails();

    // TODO details..what are they?
//    setLastRecordForDetails(details);
//
//    final Multiset<Particle> particles = TreeMultiset.create();
//    particles.addAll(getCurrentParticles());
//    details.setParticles(particles);

    return details;
  }

  /****
   * Service methods
   */
  public NycQueuedInferredLocationBean getCurrentStateAsNycQueuedInferredLocationBean() {
    final NycTestInferredLocationRecord tilr = getCurrentState();
    final NycQueuedInferredLocationBean record = RecordLibrary.getNycTestInferredLocationRecordAsNycQueuedInferredLocationBean(tilr);

    if (_particles == null)
      return null;
    final MtaVehicleState state = getAverageVehicleState();;
    final RunState runState = state.getRunStateBelief().getMaxValueKey();
    final Observation obs = (Observation) state.getObservation();
    final BlockStateObservation blockState = runState.getBlockStateObs();
    final NycRawLocationRecord nycRawRecord = obs.getRecord();
    record.setBearing(nycRawRecord.getBearing());
    
    if (blockState != null) {
      final ScheduledBlockLocation blockLocation = blockState.getBlockState().getBlockLocation();

      // set sched. dev. if we have a match in UTS and are therefore comfortable
      // saying that this schedule deviation is a true match to the schedule.
      if (blockState.isRunFormal()) {
        final int deviation = 
        		(int)((record.getRecordTimestamp() - record.getServiceDate()) / 1000 - blockLocation.getScheduledTime());

        record.setScheduleDeviation(deviation);
      } else {
        record.setScheduleDeviation(null);
      }

      // distance along trip
      final BlockTripEntry activeTrip = blockLocation.getActiveTrip();
      final double distanceAlongTrip = blockLocation.getDistanceAlongBlock() - activeTrip.getDistanceAlongBlock();
      record.setDistanceAlongTrip(distanceAlongTrip);
    }

    return record;
  }

  private MtaVehicleState getAverageVehicleState() {
    final DefaultDataDistribution<String> edgeDist = new DefaultDataDistribution<String>();
    for (VehicleState state : _particles.getDomain()) {
      BlockStateObservation blockStateObs = ((MtaVehicleState)state).getRunStateBelief()
          .getMaxValueKey().getBlockStateObs();
      edgeDist.increment(
          blockStateObs != null ? blockStateObs.getBlockState().getRunTripEntry().getRunId() : null);
    }
    final String bestRun = edgeDist.getMaxValueKey();
    
    MtaVehicleState bestPathVehicleState = null;
    MultivariateGaussian avgLoc = null;
    for (VehicleState state : Iterables.filter(_particles.getDomain(), new Predicate<VehicleState>() {
      @Override
      public boolean apply(VehicleState input) {
        RunState runState = ((MtaVehicleState)input).getRunStateBelief().getMaxValueKey();
        if (runState.getBlockStateObs() == null)
          return false;
        
        return runState.getBlockStateObs().getBlockState().getRunTripEntry().getRunId()
          .equals(bestRun);
      }
    })) {
      if (avgLoc == null)
        avgLoc = state.getBelief().getGlobalStateBelief();
      else
        avgLoc.convolve(state.getBelief().getGlobalStateBelief());
      
      bestPathVehicleState = (MtaVehicleState) state;
    }
    
    final MtaVehicleState naiveBestState = (MtaVehicleState) _particles.getMaxValueKey();
    
    final MtaVehicleState avgState;
    if (bestPathVehicleState != null) {
//      final Observation obs = (Observation)naiveBestState.getObservation();
//      final PathStateBelief avgPathStateBelief = bestPathVehicleState.getBelief().getPath().getStateBeliefOnPath(avgLoc);
//      final RunStateEstimator estimator = new RunStateEstimator(this._trackingGraph,
//          obs, avgPathStateBelief, 
//          prevAvgVehicleState != null ? prevAvgVehicleState.getRunStateBelief().getMaxValueKey().getVehicleState() : null,
//          this._trackingGraph.getRng());
//      final DataDistribution<RunState> avgRunStateBelief = estimator.createInitialLearnedObject();
//      avgState = (MtaVehicleState) this._trackingGraph.createVehicleState(obs, bestPathVehicleState.getMovementFilter(),
//          avgPathStateBelief, bestPathVehicleState.getEdgeTransitionDist(), prevAvgVehicleState);
//      avgState.setRunStateBelief(avgRunStateBelief);
      avgState = bestPathVehicleState;
    } else {
      avgState = naiveBestState; 
    }
    
    prevAvgVehicleState = currentAvgVehicleState;
    currentAvgVehicleState = avgState; 
    
    return avgState;
  }

  public NycVehicleManagementStatusBean getCurrentManagementState() {
    final NycVehicleManagementStatusBean record = new NycVehicleManagementStatusBean();

    if (_particles == null)
      return null;
    final MtaVehicleState state = getAverageVehicleState();;
    final RunState runState = state.getRunStateBelief().getMaxValueKey();
    final Observation obs = (Observation) state.getObservation();
    final BlockStateObservation blockState = runState.getBlockStateObs();
    final NycRawLocationRecord nycRawRecord = obs.getRecord();

    record.setUUID(nycRawRecord.getUuid());
    record.setInferenceIsEnabled(true);
    record.setLastUpdateTime(_lastUpdateTime);
    record.setLastLocationUpdateTime(_lastLocationUpdateTime);
    record.setAssignedRunId(obs.getOpAssignedRunId());
    record.setMostRecentObservedDestinationSignCode(nycRawRecord.getDestinationSignCode());
    record.setLastObservedLatitude(nycRawRecord.getLatitude());
    record.setLastObservedLongitude(nycRawRecord.getLongitude());
    record.setEmergencyFlag(nycRawRecord.isEmergencyFlag());

    if (blockState != null) {
      record.setLastInferredDestinationSignCode(blockState.getBlockState().getDestinationSignCode());
      record.setInferredRunId(blockState.getBlockState().getRunId());
      record.setInferenceIsFormal(blockState.isRunFormal());
    }

    return record;
  }

  /****
   * Private Methods
   ****/
  private void setLastRecordForDetails(VehicleLocationDetails details) {
    NycRawLocationRecord lastRecord = null;
    if (_previousObservation != null)
      lastRecord = _previousObservation.getRecord();

    if (lastRecord == null && _nycTestInferredLocationRecord != null) {
      lastRecord = new NycRawLocationRecord();
      lastRecord.setDestinationSignCode(_nycTestInferredLocationRecord.getDsc());
      lastRecord.setTime(_nycTestInferredLocationRecord.getTimestamp());
      lastRecord.setTimeReceived(_nycTestInferredLocationRecord.getTimestamp());
      lastRecord.setLatitude(_nycTestInferredLocationRecord.getLat());
      lastRecord.setLongitude(_nycTestInferredLocationRecord.getLon());
      lastRecord.setOperatorId(_nycTestInferredLocationRecord.getOperatorId());
      final String[] runInfo = StringUtils.splitByWholeSeparator(
          _nycTestInferredLocationRecord.getReportedRunId(), "-");

      if (runInfo != null && runInfo.length > 0) {
        lastRecord.setRunRouteId(runInfo[0]);
        if (runInfo.length > 1)
          lastRecord.setRunNumber(runInfo[1]);
      }
    }

    details.setLastObservation(lastRecord);
  }

  /**
   * This method rechecks the operator assignment and returns either the old
   * run-results or new ones.
   * 
   * @param observation
   * @param results
   * @return
   */
  private RunResults updateRunIdMatches(NycRawLocationRecord observation,
      RunResults results) {

    final Date obsDate = new Date(observation.getTime());

    String opAssignedRunId = null;
    final String operatorId = observation.getOperatorId();

    final boolean noOperatorIdGiven = StringUtils.isEmpty(operatorId)
        || StringUtils.containsOnly(operatorId, "0");

    final Set<AgencyAndId> routeIds = Sets.newHashSet();
    if (!noOperatorIdGiven) {
      try {
        final OperatorAssignmentItem oai = _operatorAssignmentService.getOperatorAssignmentItemForServiceDate(
            new ServiceDate(obsDate), new AgencyAndId(
                observation.getVehicleId().getAgencyId(), operatorId));

        if (oai != null) {
          if (_runService.isValidRunId(oai.getRunId())) {
            opAssignedRunId = oai.getRunId();

            /*
             * same results; we're done
             */
            if (opAssignedRunId.equals(results.getAssignedRunId()))
              return results;

            /*
             * new assigned run-id; recompute the routes
             */
            routeIds.addAll(_runService.getRoutesForRunId(opAssignedRunId));
            for (final String runId : results.getFuzzyMatches()) {
              routeIds.addAll(_runService.getRoutesForRunId(runId));
            }
            return new RunResults(opAssignedRunId, results.getFuzzyMatches(),
                results.getBestFuzzyDist(), routeIds);
          }
        }
      } catch (final Exception e) {
        _log.warn(e.getMessage());
      }
    }
    /*
     * if we're here, then the op-assignment call probably failed, so just
     * return the old results
     */
    return results;
  }

  private RunResults findRunIdMatches(NycRawLocationRecord observation) {
    final Date obsDate = new Date(observation.getTime());

    String opAssignedRunId = null;
    final String operatorId = observation.getOperatorId();

    final boolean noOperatorIdGiven = StringUtils.isEmpty(operatorId)
        || StringUtils.containsOnly(operatorId, "0");

    final Set<AgencyAndId> routeIds = Sets.newHashSet();
    if (!noOperatorIdGiven) {
      try {
        final OperatorAssignmentItem oai = _operatorAssignmentService.getOperatorAssignmentItemForServiceDate(
            new ServiceDate(obsDate), new AgencyAndId(
                observation.getVehicleId().getAgencyId(), operatorId));

        if (oai != null) {
          if (_runService.isValidRunId(oai.getRunId())) {
            opAssignedRunId = oai.getRunId();
            routeIds.addAll(_runService.getRoutesForRunId(opAssignedRunId));
          }
        }
      } catch (final Exception e) {
        _log.warn(e.getMessage());
      }
    }

    Set<String> fuzzyMatches = Collections.emptySet();
    final String reportedRunId = observation.getRunId();
    Integer bestFuzzyDistance = null;
    if (StringUtils.isEmpty(opAssignedRunId) && !noOperatorIdGiven) {
      _log.info("no assigned run found for operator=" + operatorId);
    }

    if (StringUtils.isNotEmpty(reportedRunId)
        && !StringUtils.containsOnly(reportedRunId, new char[] {'0', '-'})) {

      try {
        final TreeMultimap<Integer, String> fuzzyReportedMatches = _runService.getBestRunIdsForFuzzyId(reportedRunId);
        if (fuzzyReportedMatches.isEmpty()) {
          _log.info("couldn't find a fuzzy match for reported runId="
              + reportedRunId);
        } else {
          bestFuzzyDistance = fuzzyReportedMatches.keySet().first();
          if (bestFuzzyDistance <= 1) {
            fuzzyMatches = fuzzyReportedMatches.get(bestFuzzyDistance);
            for (final String runId : fuzzyMatches) {
              routeIds.addAll(_runService.getRoutesForRunId(runId));
            }
          }
        }
      } catch (final IllegalArgumentException ex) {
        _log.warn(ex.getMessage());
      }
    }

    return new RunResults(opAssignedRunId, fuzzyMatches, bestFuzzyDistance,
        routeIds);
  }

  /**
   * Returns the most recent inference result as an NycTestInferredLocationRecord, i.e. simulator output.
   * @return
   */
  private NycTestInferredLocationRecord getMostRecentParticleAsNycTestInferredLocationRecord() {
    
    if (_particles == null)
      return null;
    
    final MtaVehicleState state = getAverageVehicleState();;
    final RunState runState = state.getRunStateBelief().getMaxValueKey();
    final Observation obs = (Observation) state.getObservation();
    final BlockStateObservation blockState = runState.getBlockStateObs();
    final MotionState motionState = runState.getVehicleState().getMotionState();
    final JourneyState journeyState = runState.getJourneyState();
    final CoordinatePoint location = obs.getLocation();
    final NycRawLocationRecord nycRecord = obs.getRecord();

    final NycTestInferredLocationRecord record = new NycTestInferredLocationRecord();
    record.setLat(location.getLat());
    record.setLon(location.getLon());
    record.setTimestamp(obs.getTime());
    record.setDsc(nycRecord.getDestinationSignCode());
    record.setOperatorId(nycRecord.getOperatorId());
    record.setReportedRunId(RunTripEntry.createId(nycRecord.getRunRouteId(),
        nycRecord.getRunNumber()));

    final EVehiclePhase phase = journeyState.getPhase();
    if (phase != null)
      record.setInferredPhase(phase.name());
    else
      record.setInferredPhase(EVehiclePhase.UNKNOWN.name());

    final Set<String> statusFields = new HashSet<String>();

    /*
     * This should make sure these are populated. (will show prev. values when
     * record lat/lon are zero)
     */
//    record.setInferredBlockLat(location.getLat());
//    record.setInferredBlockLon(location.getLon());
    Coordinate stateMeanGps;
    try {
      stateMeanGps = GeoUtils.convertToLatLon(state.getMeanLocation(), 
          obs.getObsProjected().getTransform());
      record.setInferredBlockLat(stateMeanGps.x);
      record.setInferredBlockLon(stateMeanGps.y);
    } catch (NoninvertibleTransformException e) {
      e.printStackTrace();
    } catch (TransformException e) {
      e.printStackTrace();
    } 
    record.setInferredStateMean(state.getBelief().getGlobalState().toString());
    record.setInferredStateCovariance(state.getBelief().getCovariance().toString());
    record.setInferredEdge(state.getBelief().getEdge().getInferredEdge().getEdgeId());

    if (blockState != null) {
      record.setInferredRunId(blockState.getBlockState().getRunId());
      record.setInferredIsRunFormal(blockState.isRunFormal());

      // formality match exposed to TDS
      if(blockState.isRunFormal()) {
        statusFields.add("blockInf");
      }

      final BlockInstance blockInstance = blockState.getBlockState().getBlockInstance();
      final BlockConfigurationEntry blockConfig = blockInstance.getBlock();
      final BlockEntry block = blockConfig.getBlock();

      record.setInferredBlockId(AgencyAndIdLibrary.convertToString(block.getId()));
      record.setInferredServiceDate(blockInstance.getServiceDate());

      final ScheduledBlockLocation blockLocation = blockState.getBlockState().getBlockLocation();
      record.setInferredDistanceAlongBlock(blockLocation.getDistanceAlongBlock());
      record.setInferredScheduleTime(blockLocation.getScheduledTime());

      final BlockTripEntry activeTrip = blockLocation.getActiveTrip();
      if (activeTrip != null) {
        final TripEntry trip = activeTrip.getTrip();
        record.setInferredTripId(AgencyAndIdLibrary.convertToString(trip.getId()));
      }

//      final CoordinatePoint locationAlongBlock = blockLocation.getLocation();
//      if (locationAlongBlock != null && 
//    		  (EVehiclePhase.IN_PROGRESS.equals(phase) || phase.toLabel().toUpperCase().startsWith("LAYOVER_"))) {
//        record.setInferredBlockLat(locationAlongBlock.getLat());
//        record.setInferredBlockLon(locationAlongBlock.getLon());
//      }

      if (EVehiclePhase.IN_PROGRESS.equals(phase)) {    	  
        final int secondsSinceLastMotion = (int) ((obs.getTime() - motionState.getLastInMotionTime()) / 1000);
        if (secondsSinceLastMotion > 
        	_configurationService.getConfigurationValueAsInteger("display.stalledTimeout", 900))
          statusFields.add("stalled");
      } else {
        // vehicles on detour should be in_progress with status=deviated 
        if (journeyState.getIsDetour()) {
          // remap this journey state/phase to IN_PROGRESS to conform to 
          // previous pilot project semantics.
          if (EVehiclePhase.DEADHEAD_DURING.equals(phase)) {
            record.setInferredPhase(EVehiclePhase.IN_PROGRESS.name());
            statusFields.add("deviated");
          }
        }
      }

      record.setInferredDsc(blockState.getBlockState().getDestinationSignCode());
    }

    // Set the status field
    if (!statusFields.isEmpty()) {
      record.setInferredStatus(StringUtils.join(statusFields, "+"));
    } else {
      record.setInferredStatus("default");
    }

    if (StringUtils.isBlank(record.getInferredDsc()))
      record.setInferredDsc("0000");

    return record;
  }

}
