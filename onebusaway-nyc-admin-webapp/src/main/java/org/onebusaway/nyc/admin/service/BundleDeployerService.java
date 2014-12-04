package org.onebusaway.nyc.admin.service;

import javax.ws.rs.core.Response;

public interface BundleDeployerService {

  public Response listStagedBundles(String environment);

  public Response getLatestBundle();

  public Response deploy(String environment);

  public Response deployStatus(String id);

  public Response getBundleFile(String bundleId, String relativeFilename);

  public Response getBundleList();

}
