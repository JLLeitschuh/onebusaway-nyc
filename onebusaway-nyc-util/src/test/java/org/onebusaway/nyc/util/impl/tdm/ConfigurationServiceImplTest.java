package org.onebusaway.nyc.util.impl.tdm;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import org.onebusaway.container.refresh.RefreshService;
import org.onebusaway.nyc.util.impl.RestApiLibrary;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.net.URL;

@RunWith(MockitoJUnitRunner.class)
public class ConfigurationServiceImplTest {

  @SuppressWarnings("unused")
  @Mock
  private RefreshService refreshService;

  @Mock
  private TransitDataManagerApiLibrary mockApiLibrary;

  @Mock
  private RestApiLibrary mockRestApiLibrary;

  @InjectMocks
  private ConfigurationServiceImpl service;

  @Before
  public void setupApiLibrary() throws Exception {
    RestApiLibrary ral = new RestApiLibrary("localhost", null, "api");    
    String json = new String("{\"config\":[{\"value\":\"20\",\"key\":\"tdm.crewAssignmentRefreshInterval\",\"description\":null,\"value-type\":\"String\",\"units\":\"minutes\",\"component\":\"tdm\",\"updated\":null}],\"status\":\"OK\"}");
    when(mockApiLibrary.getItemsForRequest("config", "list"))
      .thenReturn(ral.getJsonObjectsForString(json));

    TransitDataManagerApiLibrary tdmal = new TransitDataManagerApiLibrary("tdm.staging.obanyc.com", 80, "/api");
    URL setUrl = tdmal.buildUrl("config", "testComponent", "test123", "set");
    when(mockRestApiLibrary.setContents(setUrl, "testValue"))
      .thenReturn(true);

    service.refreshConfiguration();
  }

  @Test
  public void testDefaultvalue() throws Exception {
    assertEquals(service.getConfigurationValueAsString("test789", "default"), "default");
  }

  @Test
  public void testGetExistingValueFromTDM() throws Exception {
    assertEquals(service.getConfigurationValueAsString("tdm.crewAssignmentRefreshInterval", null), "20");
  }

  @Test
  public void testGetExistingValueFromTDMDefaultOverridden() throws Exception {
    assertEquals(service.getConfigurationValueAsString("tdm.crewAssignmentRefreshInterval", "30"), "20");
  }

  @Test
  public void setValue() throws Exception {
    service.setConfigurationValue("testComponent", "test123", "testValue");
    assertEquals(service.getConfigurationValueAsString("test123", null), "testValue");
  }
}
