package org.onebusaway.nyc.admin.service;

import javax.ws.rs.core.Response;

public interface BundleArchiverService {

  public Response getArchiveBundleList();

  public Response getFileByName(String dataset, String name, String file);

  public Response getFileById(String id, String file);

}
