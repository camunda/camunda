/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.jetty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
import static org.camunda.optimize.OptimizeJettyServerCustomizer.EXTERNAL_SUB_PATH;
import static org.camunda.optimize.jetty.OptimizeResourceConstants.REST_API_PATH;

import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import java.util.Optional;
import org.camunda.optimize.AbstractPlatformIT;
import org.camunda.optimize.rest.UIConfigurationRestService;
import org.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag(OPENSEARCH_PASSING)
public class ContextPathIT extends AbstractPlatformIT {

  @AfterAll
  public static void afterAll() {
    embeddedOptimizeExtension.getConfigurationService().setContextPath(null);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/customContextPath", "/customContextPath/subPath"})
  public void customConfigPathIsCorrectlyApplied(final String customContextPath) {
    // given
    embeddedOptimizeExtension.getConfigurationService().setContextPath(customContextPath);
    startAndUseNewOptimizeInstance();

    // then the request executor uses the custom context path
    assertThat(
            embeddedOptimizeExtension.getRequestExecutor().getDefaultWebTarget().getUri().getPath())
        .contains(customContextPath);

    // when a static resource is requested
    Response response = embeddedOptimizeExtension.rootTarget("index.html").request().get();

    // then
    assertThat(embeddedOptimizeExtension.rootTarget("index.html").getUri().getPath())
        .contains(customContextPath);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    // when an unauthenticated resource is requested
    response = embeddedOptimizeExtension.getRequestExecutor().buildGetReadinessRequest().execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    // when an authenticated resource is requested
    response =
        embeddedOptimizeExtension.getRequestExecutor().buildGetAllEntitiesRequest().execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    // when a public API resource is requested
    response = publicApiClient.toggleSharing(true, getAccessToken());

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());

    // when a resource on the external subpath is requested
    response =
        embeddedOptimizeExtension
            .rootTarget(
                REST_API_PATH
                    + EXTERNAL_SUB_PATH
                    + UIConfigurationRestService.UI_CONFIGURATION_PATH)
            .request()
            .get();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    // when the custom path is not included as part of the request
    final String optimizeEndpoint =
        IntegrationTestConfigurationUtil.getEmbeddedOptimizeEndpoint(
            embeddedOptimizeExtension.getApplicationContext());
    final WebTarget requestWithoutContextPath =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .createWebTarget(
                optimizeEndpoint.substring(0, optimizeEndpoint.lastIndexOf(customContextPath)))
            .path("index.html");
    response = requestWithoutContextPath.request().get();

    // then the resource is not found
    assertThat(requestWithoutContextPath.getUri().getPath()).doesNotContain(customContextPath);
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  private String getAccessToken() {
    return Optional.ofNullable(
            embeddedOptimizeExtension
                .getConfigurationService()
                .getOptimizeApiConfiguration()
                .getAccessToken())
        .orElseGet(
            () -> {
              String randomToken = "1_2_Polizei";
              embeddedOptimizeExtension
                  .getConfigurationService()
                  .getOptimizeApiConfiguration()
                  .setAccessToken(randomToken);
              return randomToken;
            });
  }
}
