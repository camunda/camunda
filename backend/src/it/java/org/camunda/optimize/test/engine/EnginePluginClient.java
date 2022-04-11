/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.engine;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;

import javax.ws.rs.core.Response;
import java.io.IOException;

@AllArgsConstructor
@Slf4j
public class EnginePluginClient {
  private static final String ENGINE_IT_PLUGIN_ENDPOINT = IntegrationTestConfigurationUtil.getEngineItPluginEndpoint();
  private static final String DEPLOY_PATH = "/deploy";
  private static final String PURGE_PATH = "/purge";

  private final CloseableHttpClient httpClient;

  @SneakyThrows
  public void deployEngine(final String engineName) {
    log.info("Deploying engine with name {}", engineName);
    final HttpPost deployRequest = new HttpPost(
      new URIBuilder(ENGINE_IT_PLUGIN_ENDPOINT + DEPLOY_PATH)
        .addParameter("name", engineName)
        .build()
    );
    try (CloseableHttpResponse response = httpClient.execute(deployRequest)) {
      final Response.Status statusCode = Response.Status.fromStatusCode(
        response.getStatusLine().getStatusCode()
      );
      switch (statusCode) {
        case OK:
          log.info("Finished deploying engine {}.", engineName);
          break;
        case CONFLICT:
          log.info("Engine with name {} was already deployed.", engineName);
          break;
        default:
          String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
          log.error("Error deploying engine {}, got status code {}. Error message was: {}", engineName, statusCode, responseString);
          throw new RuntimeException("Something really bad happened during engine deployment, please check the logs.");
      }
    } catch (IOException e) {
      final String message = String.format("Error deploying engine %s.", engineName);
      log.error(message, engineName, e);
      throw new OptimizeIntegrationTestException(message, e);
    }
  }

  @SneakyThrows
  public void cleanEngine(final String engineName) {
    log.info("Start cleaning engine");
    final HttpPost purgeRequest = new HttpPost(
      new URIBuilder(ENGINE_IT_PLUGIN_ENDPOINT + PURGE_PATH)
        .addParameter("name", engineName)
        .build()
    );
    try (CloseableHttpResponse response = httpClient.execute(purgeRequest)) {
      if (response.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
        throw new RuntimeException("Something really bad happened during purge, please check the logs.");
      }
      log.info("Finished cleaning engine");
    } catch (IOException e) {
      final String message = "Error cleaning engine with purge request";
      log.error(message, e);
      throw new OptimizeIntegrationTestException(message, e);
    }
  }

}
