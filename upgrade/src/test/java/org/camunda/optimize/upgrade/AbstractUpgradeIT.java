package org.camunda.optimize.upgrade;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.After;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public abstract class AbstractUpgradeIT {

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

  @After
  public void after() {
    cleanAllDataFromElasticsearch();
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

  protected void cleanAllDataFromElasticsearch() {
    try {
      restClient.performRequest("DELETE", "_all", Collections.emptyMap());
    } catch (IOException e) {
      //nothing to do
    }
  }

}
