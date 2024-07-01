/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// package io.camunda.optimize.jetty;

// TODO recreate C8 IT equivalent of this with #13337

// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static io.camunda.optimize.jetty.OptimizeResourceConstants.NO_CACHE_RESOURCES;
// import static io.camunda.optimize.rest.constants.RestConstants.CACHE_CONTROL_NO_STORE;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.AbstractPlatformIT;
// import jakarta.ws.rs.core.HttpHeaders;
// import jakarta.ws.rs.core.Response;
// import java.util.stream.Stream;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.params.ParameterizedTest;
// import org.junit.jupiter.params.provider.MethodSource;
//
// @Tag(OPENSEARCH_PASSING)
// public class NoCachingIT extends AbstractPlatformIT {
//
//  @ParameterizedTest
//  @MethodSource("noCacheResources")
//  public void loadingOfStaticResourcesContainsNoCacheHeader(String staticResource) {
//    // when
//    Response response = embeddedOptimizeExtension.rootTarget(staticResource).request().get();
//
//    // then
//    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
//    assertThat(response.getHeaderString(HttpHeaders.CACHE_CONTROL))
//        .isEqualTo(CACHE_CONTROL_NO_STORE);
//  }
//
//  @Test
//  public void restApiCallResponseContainsNoCacheHeader() {
//    // when
//    Response response =
//        embeddedOptimizeExtension.getRequestExecutor().buildCheckImportStatusRequest().execute();
//
//    // then
//    assertThat(response.getHeaderString(HttpHeaders.CACHE_CONTROL))
//        .isEqualTo(CACHE_CONTROL_NO_STORE);
//  }
//
//  private static Stream<String> noCacheResources() {
//    return NO_CACHE_RESOURCES.stream();
//  }
// }
