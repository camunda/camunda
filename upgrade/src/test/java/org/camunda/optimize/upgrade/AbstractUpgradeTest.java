package org.camunda.optimize.upgrade;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.camunda.optimize.upgrade.service.ValidationService;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public abstract class AbstractUpgradeTest {

  public static final String MAPPING = "/_mapping";

  public static final String PUT = "PUT";
  public static final String OPTIMIZE_METADATA = "optimize-metadata";
  protected RestClient restClient;

  protected void initClient() {
    if (restClient == null) {
      restClient = RestClient.builder(
        new HttpHost(
          "localhost",
          9200,
          "http"
        )
      ).build();
    }
  }

  protected void addVersionToElasticsearch(String version) throws IOException {
    restClient.performRequest(PUT, OPTIMIZE_METADATA, Collections.emptyMap());
    String data = "{\n" +
      "  \"schemaVersion\": \"" + version + "\"\n" +
      "}";
    HttpEntity entity = new NStringEntity(data, ContentType.APPLICATION_JSON);
    HashMap<String, String> refreshParams = new HashMap<>();
    refreshParams.put("refresh", "true");
    Response post = restClient.performRequest(PUT, OPTIMIZE_METADATA + "/metadata/1", refreshParams, entity);
    assertThat(post.getStatusLine().getStatusCode(), is(201));
  }

  protected void removeTaskIndex() {
    try {
      restClient.performRequest("DELETE", ".tasks", Collections.emptyMap());
    } catch (IOException e) {
      //nothing to do
    }
  }

  protected void removeVersionIndex() {
    try {
      restClient.performRequest("DELETE", OPTIMIZE_METADATA, Collections.emptyMap());
    } catch (IOException e) {
      //nothing to do
    }
  }

  protected void cleanAllDataFromElasticsearch() {
    try {
      restClient.performRequest("DELETE", "_all", Collections.emptyMap());
    } catch (IOException e) {
      //nothing to do
    }
  }

  protected void createEmptyEnvConfig() throws Exception {
    createEnvConfig("");
  }

  protected void createEnvConfig(String content) throws Exception {
    File env = createEnvFolder();
    File config = new File(env.getAbsolutePath() + "/environment-config.yaml");

    if (!config.exists()) {
      config.createNewFile();
      FileWriter fileWriter = new FileWriter(config);
      if (content != null && !content.isEmpty()) {
        fileWriter.append(content);
      } else {
        fileWriter.write("");
      }
      fileWriter.close();
    }
  }

  protected File createEnvFolder() throws Exception {
    File env = getEnvFolder();
    if (!env.exists()) {
      env.mkdirs();
    }
    return env;
  }

  protected void deleteEnvFolderWithConfig() throws Exception {
    deleteEnvConfig();
    deleteEnvFolder();
  }

  protected void deleteEnvFolder() throws Exception {
    File env = getEnvFolder();
    if (env.exists()) {
      env.delete();
    }
  }

  protected void deleteEnvConfig() throws Exception {
    File env = getEnvFolder();
    File config = new File(env.getAbsolutePath() + "/environment-config.yaml");

    if (config.exists()) {
      config.delete();
    }
  }

  protected File getEnvFolder() throws URISyntaxException {
    String executionFolderPath =
      ValidationService.class.
        getProtectionDomain()
        .getCodeSource()
        .getLocation()
        .toURI()
        .getPath();
    return new File(executionFolderPath + "/../environment");
  }
}
