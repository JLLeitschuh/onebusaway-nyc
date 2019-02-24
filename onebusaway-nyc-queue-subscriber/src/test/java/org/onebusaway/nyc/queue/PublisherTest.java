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
package org.onebusaway.nyc.queue;

import com.eaio.uuid.UUID;
import com.google.gson.Gson;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.AnnotationIntrospector;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.xc.JaxbAnnotationIntrospector;
import org.junit.Before;
import org.onebusaway.nyc.queue.model.RealtimeEnvelope;
import org.junit.Test;
import lrms_final_09_07.Angle;
import tcip_final_3_0_5_1.CcLocationReport;
import tcip_final_3_0_5_1.CPTOperatorIden;
import tcip_final_3_0_5_1.CPTVehicleIden;
import tcip_final_3_0_5_1.SCHRouteIden;
import tcip_final_3_0_5_1.SCHRunIden;
import tcip_final_3_0_5_1.SPDataQuality;
import tcip_3_0_5_local.NMEA;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import static org.junit.Assert.*;


public class PublisherTest {

    RmcUtil rmcUtil = new RmcUtil();

    @Test
    public void testOutput() {
        RealtimeEnvelope re = new RealtimeEnvelope();
        re.setUUID(new UUID().toString());
        re.setTimeReceived(System.currentTimeMillis());
        re.setCcLocationReport(buildCcLocationReport());
        Gson gson = new Gson();
        gson.toJson(re);
    }

    @Test
    public void testWrap() throws Exception {

        Publisher p = new Publisher("topic") {
            String generateUUID() {
                return "foo";
            }
            long getTimeReceived() {
                return 1234567;
            }
        };
        ObjectMapper _mapper = new ObjectMapper();
        /* use Jaxb annotation interceptor so we pick up autogenerated annotations from XSDs */
        AnnotationIntrospector jaxb = new JaxbAnnotationIntrospector();
        _mapper.getDeserializationConfig().setAnnotationIntrospector(jaxb);

        String ccmessage = "{\"CcLocationReport\": {\"request-id\" : 528271,\"vehicle\": {\"vehicle-id\": 7579,\"agency-id\": 2008,\"agencydesignator\": \"MTA NYCT\"},\"status-info\": 0,\"time-reported\": \"2011-10-15T03:26:19.000-00:00\",\"latitude\": 40612060,\"longitude\": -74035771,\"direction\": {\"deg\": 128.77},\"speed\": 0,\"manufacturer-data\": \"VFTP123456789\",\"operatorID\": {\"operator-id\": 0,\"designator\": \"\"},\"runID\": {\"run-id\": 0,\"designator\": \"\"},\"destSignCode\": 4631,\"routeID\": {\"route-id\": 0,\"route-designator\": \"\"},\"localCcLocationReport\": {\"NMEA\": {\"sentence\": [\"\",\"\"]}}}}";
        String rtmessage = "{\"RealtimeEnvelope\": {\"UUID\":\"foo\",\"timeReceived\": 1234567,\"CcLocationReport\": {\"request-id\" : 528271,\"vehicle\": {\"vehicle-id\": 7579,\"agency-id\": 2008,\"agencydesignator\": \"MTA NYCT\"},\"status-info\": 0,\"time-reported\": \"2011-10-15T03:26:19.000-00:00\",\"latitude\": 40612060,\"longitude\": -74035771,\"direction\": {\"deg\": 128.77},\"speed\": 0,\"manufacturer-data\": \"VFTP123456789\",\"operatorID\": {\"operator-id\": 0,\"designator\": \"\"},\"runID\": {\"run-id\": 0,\"designator\": \"\"},\"destSignCode\": 4631,\"routeID\": {\"route-id\": 0,\"route-designator\": \"\"},\"localCcLocationReport\": {\"NMEA\": {\"sentence\": [\"\",\"\"]}}}}}";

        //System.out.println("initial jackson=\n" + rtmessage);
        // now wrap message
        String envelope = p.wrap(ccmessage.getBytes());
        //System.out.println("wrapped message=\n" + envelope);
        // check what wrap sends us
        assertEquals(rtmessage, envelope);
        // now deserialize validating JSON
        JsonNode wrappedMessage = _mapper.readValue(envelope, JsonNode.class);
        String realtimeEnvelopeString = wrappedMessage.get("RealtimeEnvelope").toString();
        //System.out.println("realtimeEnvelopeString=\n" + realtimeEnvelopeString);
        RealtimeEnvelope env = _mapper.readValue(realtimeEnvelopeString, RealtimeEnvelope.class);
    }

    @Test
    public void testWrapWithNewLine() throws Exception  {
        Publisher p = new Publisher("topic") {
            String generateUUID() {
                return "foo";
            }
            long getTimeReceived() {
                return 1234567;
            }
        };
        ObjectMapper _mapper = new ObjectMapper();
        /* use Jaxb annotation interceptor so we pick up autogenerated annotations from XSDs */
        AnnotationIntrospector jaxb = new JaxbAnnotationIntrospector();
        _mapper.getDeserializationConfig().setAnnotationIntrospector(jaxb);

        String ccmessage = "{\"CcLocationReport\": {\"request-id\" : 528271,\"vehicle\": {\"vehicle-id\": 7579,\"agency-id\": 2008,\"agencydesignator\": \"MTA NYCT\"},\"status-info\": 0,\"time-reported\": \"2011-10-15T03:26:19.000-00:00\",\"latitude\": 40612060,\"longitude\": -74035771,\"direction\": {\"deg\": 128.77},\"speed\": 0,\"manufacturer-data\": \"VFTP123456789\",\"operatorID\": {\"operator-id\": 0,\"designator\": \"\"},\"runID\": {\"run-id\": 0,\"designator\": \"\"},\"destSignCode\": 4631,\"routeID\": {\"route-id\": 0,\"route-designator\": \"\"},\"localCcLocationReport\": {\"NMEA\": {\"sentence\": [\"\",\"\"]}}}}\n";
        String rtmessage = "{\"RealtimeEnvelope\": {\"UUID\":\"foo\",\"timeReceived\": 1234567,\"CcLocationReport\": {\"request-id\" : 528271,\"vehicle\": {\"vehicle-id\": 7579,\"agency-id\": 2008,\"agencydesignator\": \"MTA NYCT\"},\"status-info\": 0,\"time-reported\": \"2011-10-15T03:26:19.000-00:00\",\"latitude\": 40612060,\"longitude\": -74035771,\"direction\": {\"deg\": 128.77},\"speed\": 0,\"manufacturer-data\": \"VFTP123456789\",\"operatorID\": {\"operator-id\": 0,\"designator\": \"\"},\"runID\": {\"run-id\": 0,\"designator\": \"\"},\"destSignCode\": 4631,\"routeID\": {\"route-id\": 0,\"route-designator\": \"\"},\"localCcLocationReport\": {\"NMEA\": {\"sentence\": [\"\",\"\"]}}}}}";

        //System.out.println("initial jackson=\n" + rtmessage);
        // now wrap message
        String envelope = p.wrap(ccmessage.getBytes());
        //System.out.println("wrapped message=\n" + envelope);
        // check what wrap sends us
        assertEquals(rtmessage, envelope);
    }


    @Test
    public void testWrapNull() throws Exception {

        Publisher p = new Publisher("topic") {
            String generateUUID() {
                return "foo";
            }
            long getTimeReceived() {
                return 1234567;
            }
        };

        String envelope = p.wrap(null);
        assertEquals(null, envelope);
    }

    @Test
    public void testWrapEmpty() throws Exception {

        Publisher p = new Publisher("topic") {
            String generateUUID() {
                return "foo";
            }
            long getTimeReceived() {
                return 1234567;
            }
        };

        String envelope = p.wrap(new byte[0]);
        assertEquals(null, envelope);
    }

    @Test
    public void testWrapSingle() throws Exception {

        Publisher p = new Publisher("topic") {
            String generateUUID() {
                return "foo";
            }
            long getTimeReceived() {
                return 1234567;
            }
        };

        byte[] buff = " ".getBytes();
        String envelope = p.wrap(buff);
        assertEquals(null, envelope);
    }

    @Test
    public void testMissingRmcData(){
        // Test Missing RMC String
        String ccmessage = "{\"CcLocationReport\":{\"request-id\":1008,\"vehicle\":{\"vehicle-id\":242,\"agency-id\":2008,\"agencydesignator\":\"MTA NYCT\"},\"status-info\":0,\"time-reported\":\"2018-07-18T03:58:58.0-00:00\",\"latitude\":40616413,\"longitude\":-74031067,\"direction\":{\"deg\":196.6},\"speed\":30,\"manufacturer-data\":\"BMV54616\",\"operatorID\":{\"operator-id\":0,\"designator\":\"460003\"},\"runID\":{\"run-id\":0,\"designator\":\"49\"},\"destSignCode\":12,\"routeID\":{\"route-id\":0,\"route-designator\":\"8\"},\"localCcLocationReport\":{\"NMEA\":{\"sentence\":[\"$GPGGA,035857.677,4036.98481,N,07401.86405,W,1,09,1.07,00037.6,M,-034.3,M,,*60\"]},\"vehiclePowerState\":1}}}";
        assertNull(rmcUtil.getRmcData(new StringBuffer(ccmessage)));

        // Test Missing Date
        ccmessage = "{\"CcLocationReport\":{\"request-id\":1008,\"vehicle\":{\"vehicle-id\":242,\"agency-id\":2008,\"agencydesignator\":\"MTA NYCT\"},\"status-info\":0,\"time-reported\":\"2018-07-18T03:58:58.0-00:00\",\"latitude\":40616413,\"longitude\":-74031067,\"direction\":{\"deg\":196.6},\"speed\":30,\"manufacturer-data\":\"BMV54616\",\"operatorID\":{\"operator-id\":0,\"designator\":\"460003\"},\"runID\":{\"run-id\":0,\"designator\":\"49\"},\"destSignCode\":12,\"routeID\":{\"route-id\":0,\"route-designator\":\"8\"},\"localCcLocationReport\":{\"NMEA\":{\"sentence\":[\"$GPRMC,035857.677,A,4036.98481,N,07401.86405,W,000.0,196.6,,,,A*79\",\"$GPGGA,035857.677,4036.98481,N,07401.86405,W,1,09,1.07,00037.6,M,-034.3,M,,*60\"]},\"vehiclePowerState\":1}}}";
        assertNull(rmcUtil.getRmcData(new StringBuffer(ccmessage)));

        // Test Missing Time
        ccmessage = "{\"CcLocationReport\":{\"request-id\":1008,\"vehicle\":{\"vehicle-id\":242,\"agency-id\":2008,\"agencydesignator\":\"MTA NYCT\"},\"status-info\":0,\"time-reported\":\"2018-07-18T03:58:58.0-00:00\",\"latitude\":40616413,\"longitude\":-74031067,\"direction\":{\"deg\":196.6},\"speed\":30,\"manufacturer-data\":\"BMV54616\",\"operatorID\":{\"operator-id\":0,\"designator\":\"460003\"},\"runID\":{\"run-id\":0,\"designator\":\"49\"},\"destSignCode\":12,\"routeID\":{\"route-id\":0,\"route-designator\":\"8\"},\"localCcLocationReport\":{\"NMEA\":{\"sentence\":[\"$GPRMC,,A,4036.98481,N,07401.86405,W,000.0,196.6,180718,,,A*79\",\"$GPGGA,035857.677,4036.98481,N,07401.86405,W,1,09,1.07,00037.6,M,-034.3,M,,*60\"]},\"vehiclePowerState\":1}}}";
        assertNull(rmcUtil.getRmcData(new StringBuffer(ccmessage)));
    }

    @Test
    public void testGetRmcDateTime() throws ParseException {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        SimpleDateFormat sdf = new SimpleDateFormat("dd-M-yyyy hh:mm:ss.S");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        String dateInString = "18-07-2018 03:58:57.677";
        Date expectedDate = sdf.parse(dateInString);

        String ccmessage = "{\"CcLocationReport\":{\"request-id\":1008,\"vehicle\":{\"vehicle-id\":242,\"agency-id\":2008,\"agencydesignator\":\"MTA NYCT\"},\"status-info\":0,\"time-reported\":\"2018-07-18T03:58:58.0-00:00\",\"latitude\":40616413,\"longitude\":-74031067,\"direction\":{\"deg\":196.6},\"speed\":30,\"manufacturer-data\":\"BMV54616\",\"operatorID\":{\"operator-id\":0,\"designator\":\"460003\"},\"runID\":{\"run-id\":0,\"designator\":\"49\"},\"destSignCode\":12,\"routeID\":{\"route-id\":0,\"route-designator\":\"8\"},\"localCcLocationReport\":{\"NMEA\":{\"sentence\":[\"$GPRMC,035857.677,A,4036.98481,N,07401.86405,W,000.0,196.6,180718,,,A*79\",\"$GPGGA,035857.677,4036.98481,N,07401.86405,W,1,09,1.07,00037.6,M,-034.3,M,,*60\"]},\"vehiclePowerState\":1}}}";
        String[] rmcData = rmcUtil.getRmcData(new StringBuffer(ccmessage));
        Date actualDate = rmcUtil.getRmcDateTime(rmcData);

        assertEquals(expectedDate, actualDate);
    }

    @Test
    public void testRmcDateIsValid() throws ParseException {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        Publisher p = new Publisher("topic") {
            String generateUUID() {
                return "foo";
            }
            long getTimeReceived() {
                return 1532712063123L;
            } // Friday, July 27, 2018 5:21:03.123 PM
        };

        Calendar cal1 = Calendar.getInstance();
        cal1.setTimeInMillis(1532712063123L);
        cal1.add(Calendar.WEEK_OF_YEAR, -1022);
        assertTrue(rmcUtil.isRmcDateValid(cal1.getTime(), p.getTimeReceived()));

        Calendar cal2 = Calendar.getInstance();
        cal2.setTimeInMillis(1532712063123L);
        cal2.add(Calendar.WEEK_OF_YEAR, -1023);
        assertFalse(rmcUtil.isRmcDateValid(cal2.getTime(), p.getTimeReceived()));

        Calendar cal3 = Calendar.getInstance();
        cal3.setTimeInMillis(1532712063123L);
        cal3.add(Calendar.WEEK_OF_YEAR, -1024);
        System.out.println(cal3.getTime());
        assertFalse(rmcUtil.isRmcDateValid(cal3.getTime(), p.getTimeReceived()));

        Calendar cal4 = Calendar.getInstance();
        cal4.setTimeInMillis(1532712063123L);
        cal4.add(Calendar.WEEK_OF_YEAR, -1025);
        assertFalse(rmcUtil.isRmcDateValid(cal4.getTime(), p.getTimeReceived()));

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(1532712063123L);
        cal.add(Calendar.WEEK_OF_YEAR, -1026);
        assertTrue(rmcUtil.isRmcDateValid(cal.getTime(), p.getTimeReceived()));
    }

    @Test
    public void testRmcTimeIsValid(){
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        Calendar cal = Calendar.getInstance();
        Date currentTime = cal.getTime();
        cal.add(Calendar.SECOND, -30);
        assertTrue(rmcUtil.isRmcTimeValid( cal.getTime(), currentTime));

        Calendar cal2 = Calendar.getInstance();
        Date currentTime2 = cal2.getTime();
        cal2.add(Calendar.SECOND, -31);
        assertFalse(rmcUtil.isRmcTimeValid( cal2.getTime(), currentTime2));
    }

    @Test
    public void testReplaceRmcData() throws ParseException {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));

       // REPLACE DATE AND TIME
        long timeReceived = 1532712063123L; // Friday, July 27, 2018 5:21:03.123 PM

        // Original Date Time - Mon Dec 1 16:24:13 EST 1998
        String ccmessage = "{\"CcLocationReport\":{\"request-id\":1008,\"vehicle\":{\"vehicle-id\":242,\"agency-id\":2008,\"agencydesignator\":\"MTA NYCT\"},\"status-info\":0,\"time-reported\":\"2018-07-18T03:58:58.0-00:00\",\"latitude\":40616413,\"longitude\":-74031067,\"direction\":{\"deg\":196.6},\"speed\":30,\"manufacturer-data\":\"BMV54616\",\"operatorID\":{\"operator-id\":0,\"designator\":\"460003\"},\"runID\":{\"run-id\":0,\"designator\":\"49\"},\"destSignCode\":12,\"routeID\":{\"route-id\":0,\"route-designator\":\"8\"},\"localCcLocationReport\":{\"NMEA\":{\"sentence\":[\"$GPRMC,052103.677,A,4036.98481,N,07401.86405,W,000.0,196.6,111298,,,A*79\",\"$GPGGA,035857.677,4036.98481,N,07401.86405,W,1,09,1.07,00037.6,M,-034.3,M,,*60\"]},\"vehiclePowerState\":1}}}";

        // Expected Date Time - July 27, 2018 05:21:03.123 PM
        String expectedCcMessage = "{\"CcLocationReport\":{\"request-id\":1008,\"vehicle\":{\"vehicle-id\":242,\"agency-id\":2008,\"agencydesignator\":\"MTA NYCT\"},\"status-info\":0,\"time-reported\":\"2018-07-27T17:21:03.123-00:00\",\"latitude\":40616413,\"longitude\":-74031067,\"direction\":{\"deg\":196.6},\"speed\":30,\"manufacturer-data\":\"BMV54616\",\"operatorID\":{\"operator-id\":0,\"designator\":\"460003\"},\"runID\":{\"run-id\":0,\"designator\":\"49\"},\"destSignCode\":12,\"routeID\":{\"route-id\":0,\"route-designator\":\"8\"},\"localCcLocationReport\":{\"NMEA\":{\"sentence\":[\"$GPRMC,172103.123,A,4036.98481,N,07401.86405,W,000.0,196.6,270718,,,A*79\",\"$GPGGA,035857.677,4036.98481,N,07401.86405,W,1,09,1.07,00037.6,M,-034.3,M,,*60\"]},\"vehiclePowerState\":1}}}";
        String actualCcMessage = rmcUtil.replaceInvalidRmcDateTime(new StringBuffer(ccmessage), timeReceived);

        assertEquals(expectedCcMessage, actualCcMessage);

        // REPLACE DATE ONLY
        timeReceived = 1532668923000L; //  Friday, July 27, 2018 05:22:03 AM

        // Original Time - 05:21:50.100
        ccmessage = "{\"CcLocationReport\":{\"request-id\":1008,\"vehicle\":{\"vehicle-id\":242,\"agency-id\":2008,\"agencydesignator\":\"MTA NYCT\"},\"status-info\":0,\"time-reported\":\"2018-07-18T03:58:58.0-00:00\",\"latitude\":40616413,\"longitude\":-74031067,\"direction\":{\"deg\":196.6},\"speed\":30,\"manufacturer-data\":\"BMV54616\",\"operatorID\":{\"operator-id\":0,\"designator\":\"460003\"},\"runID\":{\"run-id\":0,\"designator\":\"49\"},\"destSignCode\":12,\"routeID\":{\"route-id\":0,\"route-designator\":\"8\"},\"localCcLocationReport\":{\"NMEA\":{\"sentence\":[\"$GPRMC,052150.100,A,4036.98481,N,07401.86405,W,000.0,196.6,111298,,,A*79\",\"$GPGGA,035857.677,4036.98481,N,07401.86405,W,1,09,1.07,00037.6,M,-034.3,M,,*60\"]},\"vehiclePowerState\":1}}}";

        // Expected Time - 05:21:50.100
        expectedCcMessage = "{\"CcLocationReport\":{\"request-id\":1008,\"vehicle\":{\"vehicle-id\":242,\"agency-id\":2008,\"agencydesignator\":\"MTA NYCT\"},\"status-info\":0,\"time-reported\":\"2018-07-27T05:21:50.100-00:00\",\"latitude\":40616413,\"longitude\":-74031067,\"direction\":{\"deg\":196.6},\"speed\":30,\"manufacturer-data\":\"BMV54616\",\"operatorID\":{\"operator-id\":0,\"designator\":\"460003\"},\"runID\":{\"run-id\":0,\"designator\":\"49\"},\"destSignCode\":12,\"routeID\":{\"route-id\":0,\"route-designator\":\"8\"},\"localCcLocationReport\":{\"NMEA\":{\"sentence\":[\"$GPRMC,052150.100,A,4036.98481,N,07401.86405,W,000.0,196.6,270718,,,A*7d\",\"$GPGGA,035857.677,4036.98481,N,07401.86405,W,1,09,1.07,00037.6,M,-034.3,M,,*60\"]},\"vehiclePowerState\":1}}}";
        actualCcMessage = rmcUtil.replaceInvalidRmcDateTime(new StringBuffer(ccmessage), timeReceived);

        assertEquals(expectedCcMessage, actualCcMessage);


        // REPLACE DATE ONLY
        timeReceived = 1550980860000L; //  Sunday, February 24, 2019 4:01:00 AM GMT

        // Original Time - 04:00:55.00
        ccmessage = "{\"CcLocationReport\":{\"request-id\":1947,\"vehicle\":{\"vehicle-id\":287,\"agency-id\":2008,\"agencydesignator\":\"MTA NYCT\"},\"status-info\":0,\"time-reported\":\"2019-02-24T03:49:46.681-00:00\",\"latitude\":40881487,\"longitude\":-73848863,\"direction\":{\"deg\":159.49},\"speed\":64,\"manufacturer-data\":\"VFTP155-603-348\",\"operatorID\":{\"operator-id\":0,\"designator\":\"41339\"},\"runID\":{\"run-id\":0,\"designator\":\"120\"},\"destSignCode\":3311,\"routeID\":{\"route-id\":0,\"route-designator\":\"31\"},\"localCcLocationReport\":{\"NMEA\":{\"sentence\":[\"$GPGGA,040055.000,4052.88924,N,07350.93179,W,1,09,01.0,+00048.0,M,,M,,*43\",\"$GPRMC,040055.00,A,4052.889241,N,07350.931792,W,015.134,159.49,110799,,,A*76\"]},\"vehiclepowerstate\":1}}}";

        // Expected Date Time - Sunday February 24, 2019 4:01:00 AM GMT
        expectedCcMessage = "{\"CcLocationReport\":{\"request-id\":1947,\"vehicle\":{\"vehicle-id\":287,\"agency-id\":2008,\"agencydesignator\":\"MTA NYCT\"},\"status-info\":0,\"time-reported\":\"2019-02-24T04:00:55.0-00:00\",\"latitude\":40881487,\"longitude\":-73848863,\"direction\":{\"deg\":159.49},\"speed\":64,\"manufacturer-data\":\"VFTP155-603-348\",\"operatorID\":{\"operator-id\":0,\"designator\":\"41339\"},\"runID\":{\"run-id\":0,\"designator\":\"120\"},\"destSignCode\":3311,\"routeID\":{\"route-id\":0,\"route-designator\":\"31\"},\"localCcLocationReport\":{\"NMEA\":{\"sentence\":[\"$GPGGA,040055.000,4052.88924,N,07350.93179,W,1,09,01.0,+00048.0,M,,M,,*43\",\"$GPRMC,040055.00,A,4052.889241,N,07350.931792,W,015.134,159.49,240219,,,A*7d\"]},\"vehiclepowerstate\":1}}}";
        actualCcMessage = rmcUtil.replaceInvalidRmcDateTime(new StringBuffer(ccmessage), timeReceived);

        assertEquals(expectedCcMessage, actualCcMessage);

    }
    @Test
    public void testGetTimeReported() throws ParseException {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));

        String ccmessage = "{\"CcLocationReport\":{\"request-id\":1008,\"vehicle\":{\"vehicle-id\":242,\"agency-id\":2008,\"agencydesignator\":\"MTA NYCT\"},\"status-info\":0,\"time-reported\":\"2018-07-18T03:58:58.0-00:00\",\"latitude\":40616413,\"longitude\":-74031067,\"direction\":{\"deg\":196.6},\"speed\":30,\"manufacturer-data\":\"BMV54616\",\"operatorID\":{\"operator-id\":0,\"designator\":\"460003\"},\"runID\":{\"run-id\":0,\"designator\":\"49\"},\"destSignCode\":12,\"routeID\":{\"route-id\":0,\"route-designator\":\"8\"},\"localCcLocationReport\":{\"NMEA\":{\"sentence\":[\"$GPRMC,035857.677,A,4036.98481,N,07401.86405,W,000.0,196.6,180790,,,A*79\",\"$GPGGA,035857.677,4036.98481,N,07401.86405,W,1,09,1.07,00037.6,M,-034.3,M,,*60\"]},\"vehiclePowerState\":1}}}";
        String expectedTimeReported = "2018-07-18T03:58:58.0-00:00";
        assertEquals(expectedTimeReported, rmcUtil.getTimeReported(new StringBuffer(ccmessage)));

        ccmessage = "{\"CcLocationReport\":{\"request-id\":1008,\"vehicle\":{\"vehicle-id\":242,\"agency-id\":2008,\"agencydesignator\":\"MTA NYCT\"},\"status-info\":0,\"latitude\":40616413,\"longitude\":-74031067,\"direction\":{\"deg\":196.6},\"speed\":30,\"manufacturer-data\":\"BMV54616\",\"operatorID\":{\"operator-id\":0,\"designator\":\"460003\"},\"runID\":{\"run-id\":0,\"designator\":\"49\"},\"destSignCode\":12,\"routeID\":{\"route-id\":0,\"route-designator\":\"8\"},\"localCcLocationReport\":{\"NMEA\":{\"sentence\":[\"$GPRMC,035857.677,A,4036.98481,N,07401.86405,W,000.0,196.6,180790,,,A*7d\",\"$GPGGA,035857.677,4036.98481,N,07401.86405,W,1,09,1.07,00037.6,M,-034.3,M,,*60\"]},\"vehiclePowerState\":1}}}";
        System.out.println(rmcUtil.getTimeReported(new StringBuffer(ccmessage)));
        assertNull(rmcUtil.getTimeReported(new StringBuffer(ccmessage)));
    }

    @Test
    public void testReplaceTimeReported() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        Publisher p = new Publisher("topic") {
            String generateUUID() {
                return "foo";
            }
            long getTimeReceived() {
                return 1532712063000L;
            } // Friday, July 27, 2018 5:21:03.000 PM
        };

        Date rmcDate = new Date(p.getTimeReceived());
        String ccmessage = "{\"CcLocationReport\":{\"request-id\":1008,\"vehicle\":{\"vehicle-id\":242,\"agency-id\":2008,\"agencydesignator\":\"MTA NYCT\"},\"status-info\":0,\"time-reported\":\"2018-07-18T03:58:58.0-00:00\",\"latitude\":40616413,\"longitude\":-74031067,\"direction\":{\"deg\":196.6},\"speed\":30,\"manufacturer-data\":\"BMV54616\",\"operatorID\":{\"operator-id\":0,\"designator\":\"460003\"},\"runID\":{\"run-id\":0,\"designator\":\"49\"},\"destSignCode\":12,\"routeID\":{\"route-id\":0,\"route-designator\":\"8\"},\"localCcLocationReport\":{\"NMEA\":{\"sentence\":[\"$GPRMC,035857.677,A,4036.98481,N,07401.86405,W,000.0,196.6,180790,,,A*79\",\"$GPGGA,035857.677,4036.98481,N,07401.86405,W,1,09,1.07,00037.6,M,-034.3,M,,*60\"]},\"vehiclePowerState\":1}}}";

        StringBuffer originalCcMessage = new StringBuffer(ccmessage);
        rmcUtil.replaceTimeReported(originalCcMessage, rmcDate);

        String expectedCcmessage = "{\"CcLocationReport\":{\"request-id\":1008,\"vehicle\":{\"vehicle-id\":242,\"agency-id\":2008,\"agencydesignator\":\"MTA NYCT\"},\"status-info\":0,\"time-reported\":\"2018-07-27T17:21:03.0-00:00\",\"latitude\":40616413,\"longitude\":-74031067,\"direction\":{\"deg\":196.6},\"speed\":30,\"manufacturer-data\":\"BMV54616\",\"operatorID\":{\"operator-id\":0,\"designator\":\"460003\"},\"runID\":{\"run-id\":0,\"designator\":\"49\"},\"destSignCode\":12,\"routeID\":{\"route-id\":0,\"route-designator\":\"8\"},\"localCcLocationReport\":{\"NMEA\":{\"sentence\":[\"$GPRMC,035857.677,A,4036.98481,N,07401.86405,W,000.0,196.6,180790,,,A*79\",\"$GPGGA,035857.677,4036.98481,N,07401.86405,W,1,09,1.07,00037.6,M,-034.3,M,,*60\"]},\"vehiclePowerState\":1}}}";

        assertEquals(expectedCcmessage,originalCcMessage.toString());

    }

    @Test
    public void testIsTimeReportedValid() throws ParseException {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        Publisher p = new Publisher("topic") {
            String generateUUID() {
                return "foo";
            }
            long getTimeReceived() {
                return 1532712063000L;
            }
        };

        Date rmcDate = new Date(p.getTimeReceived()); // Friday, July 27, 2018 5:21:03.000 PM

        assertFalse(rmcUtil.isTimeReportedValid("2018-07-27T17:21:34.0-00:00", rmcDate));
        assertTrue(rmcUtil.isTimeReportedValid("2018-07-27T17:21:01.0-00:00", rmcDate));
    }


    private CcLocationReport buildCcLocationReport() {
        CcLocationReport m = new CcLocationReport();
        m.setRequestId(1205);
        m.setDataQuality(new SPDataQuality());
        m.getDataQuality().setQualitativeIndicator("4");
        m.setDestSignCode(4631l);
        m.setDirection(new Angle());
        m.getDirection().setDeg(new BigDecimal(128.77));
        m.setLatitude(40640760);
        m.setLongitude(-74018234);
        m.setManufacturerData("VFTP123456789");
        m.setOperatorID(new CPTOperatorIden());
        m.getOperatorID().setOperatorId(0);
        m.getOperatorID().setDesignator("123456");
        m.setRequestId(1);
        m.setRouteID(new SCHRouteIden());
        m.getRouteID().setRouteId(0);
        m.getRouteID().setRouteDesignator("63");
        m.setRunID(new SCHRunIden());
        m.getRunID().setRunId(0);
        m.getRunID().setDesignator("1");
        m.setSpeed((short)36);
        m.setStatusInfo(0);
        m.setTimeReported("2011-06-22T10:58:10.0-00:00");
        m.setVehicle(new CPTVehicleIden());
        m.getVehicle().setAgencydesignator("MTA NYCT");
        m.getVehicle().setAgencyId(2008l);
        m.getVehicle().setVehicleId(2560);
        m.setLocalCcLocationReport(new tcip_3_0_5_local.CcLocationReport());
        m.getLocalCcLocationReport().setNMEA(new NMEA());
        m.getLocalCcLocationReport().getNMEA().getSentence().add("$GPRMC,105850.00,A,4038.445646,N,07401.094043,W,002.642,128.77,220611,,,A*7C");
        m.getLocalCcLocationReport().getNMEA().getSentence().add("$GPGGA,105850.000,4038.44565,N,07401.09404,W,1,09,01.7,+00042.0,M,,M,,*49");
        return m;
    }

}
