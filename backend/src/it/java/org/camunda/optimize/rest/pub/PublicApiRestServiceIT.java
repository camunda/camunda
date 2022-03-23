/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.pub;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class PublicApiRestServiceIT extends AbstractIT {

  private static final String ACCESS_TOKEN = "1_2_Polizei";

  @BeforeEach
  public void beforeTest() {
    embeddedOptimizeExtension.getConfigurationService().getOptimizeApiConfiguration().setAccessToken(ACCESS_TOKEN);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("publicApiRequestWithoutAccessTokenSupplier")
  public void executePublicApiRequestWithoutAuthorization(final String name,
                                                          final Supplier<OptimizeRequestExecutor> apiRequestExecutorSupplier) {
    // when executing a public API request without accessToken
    final Response response = apiRequestExecutorSupplier.get().execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("publicApiRequestWithInvalidTokenSupplier")
  public void executePublicApiRequestWithInvalidToken(final String name,
                                                      final Supplier<OptimizeRequestExecutor> apiRequestExecutorSupplier) {
    // when executing a public API request with invalid accessToken
    final Response response = apiRequestExecutorSupplier.get().execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("publicApiRequestWithoutAccessTokenButWithCookieSupplier")
  public void executePublicApiRequestWithoutAccessTokenButWithCookie(final String name,
                                                      final Supplier<OptimizeRequestExecutor> apiRequestExecutorSupplier) {
    // when executing a public API request with invalid accessToken
    final Response response = apiRequestExecutorSupplier.get().execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("publicApiRequestWithoutRequiredCollectionIdSupplier")
  public void executePublicApiRequestWithoutRequiredCollectionId(final String name,
                                                                 final Supplier<OptimizeRequestExecutor> apiRequestExecutorSupplier) {
    // when executing a request which usually requires a collectionId without a collectionId
    final Response response = apiRequestExecutorSupplier.get().execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    assertThat(response.readEntity(String.class)).contains("Must specify a collection ID for this request.");
  }

  @Test
  public void failGracefullyWhenNoSecretIsConfigured() {

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildPublicExportJsonReportResultRequest("fake_id", null)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  private Stream<Arguments> publicApiRequestWithoutAccessTokenSupplier() {
    return Stream.of(
      Arguments.of(
        "Export Report Result",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .withoutAuthentication()
          .buildPublicExportJsonReportResultRequest("fake_id", null)
      ), Arguments.of(
        "Export Report Definition",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .withoutAuthentication()
          .buildPublicExportJsonReportDefinitionRequest(Collections.singletonList("fake_id"), null)
      ),
      Arguments.of(
        "Export Dashboard Definition",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .withoutAuthentication()
          .buildPublicExportJsonDashboardDefinitionRequest(Collections.singletonList("fake_id"), null)
      ),
      Arguments.of(
        "Import Entity",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .withoutAuthentication()
          .buildPublicImportEntityDefinitionsRequest("fake_id", Collections.emptySet(), null)
      ),
      Arguments.of(
        "Delete Report",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .withoutAuthentication()
          .buildPublicDeleteReportRequest("fake_id", null)
      ),
      Arguments.of(
        "Get ReportIds in Collection",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .withoutAuthentication()
          .buildPublicGetAllReportIdsInCollectionRequest("fake_id", null)
      ),
      Arguments.of(
        "Get DashboardIds in Collection",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .withoutAuthentication()
          .buildPublicGetAllDashboardIdsInCollectionRequest("fake_id", null)
      )
    );
  }

  private Stream<Arguments> publicApiRequestWithoutAccessTokenButWithCookieSupplier() {
    // Omitting the .withoutAuthentication() call so that the request has a normal optimize cookie, but no
    // credentials for the public API
    return Stream.of(
      Arguments.of(
        "Export Report Result",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .buildPublicExportJsonReportResultRequest("fake_id", null)
      ), Arguments.of(
        "Export Report Definition",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .buildPublicExportJsonReportDefinitionRequest(Collections.singletonList("fake_id"), null)
      ),
      Arguments.of(
        "Export Dashboard Definition",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .buildPublicExportJsonDashboardDefinitionRequest(Collections.singletonList("fake_id"), null)
      ),
      Arguments.of(
        "Import Entity",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .buildPublicImportEntityDefinitionsRequest("fake_id", Collections.emptySet(), null)
      ),
      Arguments.of(
        "Delete Report",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .buildPublicDeleteReportRequest("fake_id", null)
      ),
      Arguments.of(
        "Get ReportIds in Collection",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .buildPublicGetAllReportIdsInCollectionRequest("fake_id", null)
      ),
      Arguments.of(
        "Get DashboardIds in Collection",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .buildPublicGetAllDashboardIdsInCollectionRequest("fake_id", null)
      )
    );
  }

  private Stream<Arguments> publicApiRequestWithInvalidTokenSupplier() {
    return Stream.of(
      Arguments.of(
        "Export Report Result",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .buildPublicExportJsonReportResultRequest("fake_id", ACCESS_TOKEN + "1")
      ), Arguments.of(
        "Export Report Definition",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .buildPublicExportJsonReportDefinitionRequest(Collections.singletonList("fake_id"), ACCESS_TOKEN + "1")
      ),
      Arguments.of(
        "Export Dashboard Definition",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .buildPublicExportJsonDashboardDefinitionRequest(Collections.singletonList("fake_id"), ACCESS_TOKEN + "1")
      ),
      Arguments.of(
        "Import Entity",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .buildPublicImportEntityDefinitionsRequest("fake_id", Collections.emptySet(), ACCESS_TOKEN + "1")
      ),
      Arguments.of(
        "Delete Report",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .buildPublicDeleteReportRequest("fake_id", ACCESS_TOKEN + "1")
      ),
      Arguments.of(
        "Get ReportIds in Collection",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .buildPublicGetAllReportIdsInCollectionRequest("fake_id", ACCESS_TOKEN + "1")
      ),
      Arguments.of(
        "Get DashboardIds in Collection",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .buildPublicGetAllDashboardIdsInCollectionRequest("fake_id", ACCESS_TOKEN + "1")
      )
    );
  }

  private Stream<Arguments> publicApiRequestWithoutRequiredCollectionIdSupplier() {
    return Stream.of(
      Arguments.of(
        "Import Entity",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .buildPublicImportEntityDefinitionsRequest(null, Collections.emptySet(), ACCESS_TOKEN)
      ),
      Arguments.of(
        "Get ReportIds in Collection",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .buildPublicGetAllReportIdsInCollectionRequest(null, ACCESS_TOKEN)
      ),
      Arguments.of(
        "Get DashboardIds in Collection",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .buildPublicGetAllDashboardIdsInCollectionRequest(null, ACCESS_TOKEN)
      )
    );
  }
}
