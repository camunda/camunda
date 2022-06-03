/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.jetty;

import org.camunda.optimize.AbstractIT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.OptimizeJettyServerCustomizer.EXTERNAL_SUB_PATH;
import static org.camunda.optimize.jetty.OptimizeResourceConstants.INDEX_HTML_PAGE;
import static org.camunda.optimize.jetty.OptimizeResourceConstants.REST_API_PATH;
import static org.camunda.optimize.rest.CandidateGroupRestService.CANDIDATE_GROUP_RESOURCE_PATH;
import static org.camunda.optimize.rest.EntitiesRestService.ENTITIES_PATH;
import static org.camunda.optimize.rest.UIConfigurationRestService.UI_CONFIGURATION_PATH;

public class ExternalSubPathRewriteIT extends AbstractIT {

  @ParameterizedTest
  @MethodSource("publicResourcesGet")
  public void externalPrefixRequestServesPublicResources(final String resourcePath) {
    // when
    Response response = embeddedOptimizeExtension
      .rootTarget(resourcePath)
      .request()
      .get();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void externalPrefixRequestAccessingSecuredResources_notFound() {
    // when
    Response response = embeddedOptimizeExtension
      .rootTarget(EXTERNAL_SUB_PATH + REST_API_PATH + ENTITIES_PATH)
      .request()
      .get();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  private static Stream<String> publicResourcesGet() {
    return Stream.of(
      // accessing the root of the webserver via the external sub-path should work
      EXTERNAL_SUB_PATH + "/",
      // explicitly accessing resources like the index.html via the external sub-path should work
      EXTERNAL_SUB_PATH + INDEX_HTML_PAGE,
      // accessing an unsecured REST API endpoint via the api/external sub-path should work
      REST_API_PATH + EXTERNAL_SUB_PATH + UI_CONFIGURATION_PATH,
      // accessing an unsecured REST API endpoint via the external sub-path should work
      EXTERNAL_SUB_PATH + REST_API_PATH + UI_CONFIGURATION_PATH,
      // accessing an unsecured REST API endpoint via the api/external sub-path should work
      EXTERNAL_SUB_PATH + REST_API_PATH + CANDIDATE_GROUP_RESOURCE_PATH,
      // accessing an unsecured REST API endpoint via the external sub-path should work
      REST_API_PATH + EXTERNAL_SUB_PATH + CANDIDATE_GROUP_RESOURCE_PATH
    );
  }

}
