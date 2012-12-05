package org.onebusaway.nyc.vehicle_tracking.impl.queue;

import org.onebusaway.nyc.vehicle_tracking.services.queue.PartitionedInputQueueListener;

/**
 * A no-op implementation for simulations with the inference engine--
 * we want to receive no actual real time queue input in that case.
 * 
 */
public class DummyPartitionedInputQueueListenerTask implements
    PartitionedInputQueueListener {

  @Override
  public String getDepotPartitionKey() {
    return null;
  }

  @Override
  public void setDepotPartitionKey(String depotPartitionKey) {

  }

  @Override
  public boolean processMessage(String address, String contents) {
    return true;
  }

}
