/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.jetty;

import org.apache.http.HttpStatus;
import org.camunda.optimize.AbstractIT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.util.stream.Stream;

import static org.camunda.optimize.jetty.NoCachingFilter.NO_STORE;
import static org.camunda.optimize.jetty.OptimizeResourceConstants.NO_CACHE_RESOURCES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class NoCachingIT extends AbstractIT {

  @ParameterizedTest
  @MethodSource("noCacheResources")
  public void loadingOfStaticResourcesContainsNoCacheHeader(String staticResource) {
    // when
    Response response = embeddedOptimizeExtension.rootTarget(staticResource).request().get();

    // then
    assertThat(response.getStatus(), is(HttpStatus.SC_OK));
    assertThat(response.getHeaderString(HttpHeaders.CACHE_CONTROL), is(NO_STORE));
  }

  @Test
  public void restApiCallResponseContainsNoCacheHeader() {
    // when
    Response response = embeddedOptimizeExtension.getRequestExecutor().buildCheckImportStatusRequest().execute();

    // then
    assertThat(response.getHeaderString(HttpHeaders.CACHE_CONTROL), is(NO_STORE));
  }

  private static Stream<String> noCacheResources() {
    return NO_CACHE_RESOURCES.stream();
  }

}
