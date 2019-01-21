package org.camunda.optimize.upgrade;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import org.camunda.optimize.upgrade.util.SchemaUpgradeUtil;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.After;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public abstract class AbstractUpgradeIT {

  public static final String PUT = "PUT";
  public static final String OPTIMIZE_METADATA = "optimize-metadata";

  protected RestHighLevelClient restClient;

  protected void initClient() {
    if (restClient == null) {
      restClient = ElasticsearchHighLevelRestClientBuilder.build(new ConfigurationService());
    }
  }

  @After
  public void after() {
    cleanAllDataFromElasticsearch();
  }

  protected void addVersionToElasticsearch(String version) throws IOException {
    restClient.getLowLevelClient().performRequest(PUT, OPTIMIZE_METADATA, Collections.emptyMap());
    String data = "{\n" +
      "  \"schemaVersion\": \"" + version + "\"\n" +
      "}";
    HttpEntity entity = new NStringEntity(data, ContentType.APPLICATION_JSON);
    HashMap<String, String> refreshParams = new HashMap<>();
    refreshParams.put("refresh", "true");
    Response post = restClient.getLowLevelClient().performRequest(PUT, OPTIMIZE_METADATA + "/metadata/1", refreshParams, entity);
    assertThat(post.getStatusLine().getStatusCode(), is(201));
  }

  protected void cleanAllDataFromElasticsearch() {
    try {
      restClient.getLowLevelClient().performRequest("DELETE", "_all", Collections.emptyMap());
    } catch (IOException e) {
      //nothing to do
    }
  }

  protected void executeBulk(final String bulkPayload) throws IOException {
    final Request request = new Request(HttpPost.METHOD_NAME, "_bulk");
    final HttpEntity entity = new NStringEntity(
      SchemaUpgradeUtil.readClasspathFileAsString(bulkPayload),
      ContentType.APPLICATION_JSON
    );
    request.setEntity(entity);
    restClient.getLowLevelClient().performRequest(request);
    restClient.indices().refresh(new RefreshRequest(), RequestOptions.DEFAULT);
  }

}
