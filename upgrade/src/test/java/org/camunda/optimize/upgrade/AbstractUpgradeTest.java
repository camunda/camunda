package org.camunda.optimize.upgrade;

import org.apache.http.HttpHost;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Askar Akhmerov
 */
public abstract class AbstractUpgradeTest {
  protected RestClient restClient;

  public static final String MAPPING = "/_mapping";


  protected RestClient initClient() {
    return RestClient.builder(
      new HttpHost(
        "localhost",
        9200,
        "http"
      )
    ).build();
  }

  protected void assertMappingAfterReindex(String expectedMapping, String finalIndexName) {
    Response response = null;
    String mappingBody = null;
    try {
      response = restClient.performRequest("GET", finalIndexName + MAPPING);
      mappingBody = EntityUtils.toString(response.getEntity());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    assertThat(response.getStatusLine().getStatusCode(), is(200));
    assertThat(mappingBody, is(expectedMapping));
  }
}
