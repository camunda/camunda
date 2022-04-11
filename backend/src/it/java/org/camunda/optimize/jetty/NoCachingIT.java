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

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.jetty.OptimizeResourceConstants.NO_CACHE_RESOURCES;
import static org.camunda.optimize.rest.constants.RestConstants.CACHE_CONTROL_NO_STORE;

public class NoCachingIT extends AbstractIT {

  @ParameterizedTest
  @MethodSource("noCacheResources")
  public void loadingOfStaticResourcesContainsNoCacheHeader(String staticResource) {
    // when
    Response response = embeddedOptimizeExtension.rootTarget(staticResource).request().get();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getHeaderString(HttpHeaders.CACHE_CONTROL)).isEqualTo(CACHE_CONTROL_NO_STORE);
  }

  @Test
  public void restApiCallResponseContainsNoCacheHeader() {
    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor().buildCheckImportStatusRequest().execute();

    // then
    assertThat(response.getHeaderString(HttpHeaders.CACHE_CONTROL)).isEqualTo(CACHE_CONTROL_NO_STORE);
  }

  private static Stream<String> noCacheResources() {
    return NO_CACHE_RESOURCES.stream();
  }

}
