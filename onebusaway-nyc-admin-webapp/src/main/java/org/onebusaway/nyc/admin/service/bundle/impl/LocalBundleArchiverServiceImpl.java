package org.onebusaway.nyc.admin.service.bundle.impl;

import java.io.File;
import java.io.FileReader;
import javax.ws.rs.core.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.onebusaway.nyc.admin.service.BundleArchiverService;
import org.onebusaway.nyc.admin.service.bundle.BundleStager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import com.google.gson.JsonParser;

@Component
@Scope("singleton")
public class LocalBundleArchiverServiceImpl implements BundleArchiverService {

  private static Logger _log = LoggerFactory.getLogger(LocalBundleArchiverServiceImpl.class);

  @Autowired
  private BundleStager bundleStager;

  private String getBundleId(File bundleDir) {
    try {
      String bundleId = new JsonParser().parse(
          new FileReader(bundleDir.getAbsolutePath() + File.separator
              + "outputs" + File.separator + "metadata.json")).getAsJsonObject().get(
          "id").getAsString();
      return bundleId;
    } catch (Exception e) {
    }
    return null;
  }

  @Override
  public Response getArchiveBundleList() {
    JSONArray response = new JSONArray();
    try {
      for (File datasetDir : new File(bundleStager.getBuiltBundleDirectory()).listFiles()) {
        String buildsDir = datasetDir.getAbsolutePath() + File.separator
            + "builds";
        try {
          for (File bundleDir : new File(buildsDir).listFiles()) {
            JSONObject bundleResponse = new JSONObject();
            bundleResponse.put("id", getBundleId(bundleDir));
            bundleResponse.put("dataset", datasetDir.getName());
            bundleResponse.put("name", bundleDir.getName());
            response.put(bundleResponse);
          }
        } catch (Exception e1) {
          _log.error("Failed to read from: " + buildsDir);
        }
      }
      return Response.ok(response.toString(), "application/json").build();
    } catch (Exception e2) {
      return Response.serverError().build();
    }
  }

  @Override
  public Response getFileByName(String dataset, String name, String file) {
    try {
      return Response.ok(
          new File(bundleStager.getBuiltBundleDirectory() + "/" + dataset
              + "/builds/" + name + "/" + file), "application/json").build();
    } catch (Exception e) {
      return Response.serverError().build();
    }
  }

  @Override
  public Response getFileById(String id, String file) {
    try {
      for (File datasetDir : new File(bundleStager.getBuiltBundleDirectory()).listFiles()) {
        File buildsDir = new File(datasetDir.getAbsolutePath() + "/builds");
        if (buildsDir.exists() && buildsDir.listFiles().length > 0) {
          for (File bundleDir : buildsDir.listFiles()) {
            try {
              if (getBundleId(bundleDir).equals(id)) {
                String filepath = bundleDir.getAbsolutePath() + "/" + file;
                return Response.ok(new File(filepath), "application/json").build();
              }
            } catch (Exception e1) {
            }
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return Response.serverError().build();
  }
}
