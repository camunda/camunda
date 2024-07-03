/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337

// package io.camunda.optimize.jetty;

// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static io.camunda.optimize.OptimizeJettyServerCustomizer.EXTERNAL_SUB_PATH;
// import static io.camunda.optimize.jetty.OptimizeResourceConstants.INDEX_HTML_PAGE;
// import static io.camunda.optimize.jetty.OptimizeResourceConstants.REST_API_PATH;
// import static io.camunda.optimize.rest.CandidateGroupRestService.CANDIDATE_GROUP_RESOURCE_PATH;
// import static io.camunda.optimize.rest.EntitiesRestService.ENTITIES_PATH;
// import static io.camunda.optimize.rest.UIConfigurationRestService.UI_CONFIGURATION_PATH;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.AbstractPlatformIT;
// import jakarta.ws.rs.core.Response;
// import java.util.stream.Stream;
// import org.junit.jupiter.api.AfterAll;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.params.ParameterizedTest;
// import org.junit.jupiter.params.provider.MethodSource;

// @Tag(OPENSEARCH_PASSING)
// public class ExternalSubPathRewriteIT extends AbstractPlatformIT {
//
//  @BeforeEach
//  public void beforeEach() {
//    setContextPath(null);
//  }
//
//  @AfterAll
//  public static void afterAll() {
//    setContextPath(null);
//  }
//
//  @ParameterizedTest
//  @MethodSource("publicResourcesGet")
//  public void externalPrefixRequestServesPublicResources(final String resourcePath) {
//    // when
//    Response response = embeddedOptimizeExtension.rootTarget(resourcePath).request().get();
//
//    // then
//    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
//  }
//
//  @Test
//  public void externalPrefixRequestAccessingSecuredResources_notFound() {
//    // when
//    Response response =
//        embeddedOptimizeExtension
//            .rootTarget(EXTERNAL_SUB_PATH + REST_API_PATH + ENTITIES_PATH)
//            .request()
//            .get();
//
//    // then
//    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
//  }
//
//  @Test
//  public void externalPrefixRequestServesPublicResourcesWithCustomContextPath() {
//    // given
//    final String contextPath = "/customContextPath";
//    setContextPath(contextPath);
//    startAndUseNewOptimizeInstance();
//
//    // then the request executor uses the custom context path
//    assertThat(
//
// embeddedOptimizeExtension.getRequestExecutor().getDefaultWebTarget().getUri().getPath())
//        .contains(contextPath);
//
//    // given
//    publicResourcesGet()
//        .forEach(
//            resourcePath -> {
//              Response response =
//                  embeddedOptimizeExtension.rootTarget(resourcePath).request().get();
//              // then
//              assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
//            });
//  }
//
//  private static Stream<String> publicResourcesGet() {
//    return Stream.of(
//        // accessing the root of the webserver via the external sub-path should work
//        EXTERNAL_SUB_PATH + "/",
//        // explicitly accessing resources like the index.html via the external sub-path should
// work
//        EXTERNAL_SUB_PATH + INDEX_HTML_PAGE,
//        // accessing an unsecured REST API endpoint via the api/external sub-path should work
//        REST_API_PATH + EXTERNAL_SUB_PATH + UI_CONFIGURATION_PATH,
//        // accessing an unsecured REST API endpoint via the external sub-path should work
//        EXTERNAL_SUB_PATH + REST_API_PATH + UI_CONFIGURATION_PATH,
//        // accessing an unsecured REST API endpoint via the api/external sub-path should work
//        EXTERNAL_SUB_PATH + REST_API_PATH + CANDIDATE_GROUP_RESOURCE_PATH,
//        // accessing an unsecured REST API endpoint via the external sub-path should work
//        REST_API_PATH + EXTERNAL_SUB_PATH + CANDIDATE_GROUP_RESOURCE_PATH);
//  }
//
//  private static void setContextPath(final String path) {
//    embeddedOptimizeExtension.getConfigurationService().setContextPath(path);
//  }
// }
