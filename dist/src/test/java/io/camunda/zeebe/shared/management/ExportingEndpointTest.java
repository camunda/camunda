/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.dynamic.config.api.ExportingStateController;
import io.camunda.zeebe.dynamic.config.api.ExportingStateController.ByTenant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;

final class ExportingEndpointTest {

  private static ExportingStateController controllerFor(
      final String tenantId, final ByTenant byTenant) {
    final var controller = mock(ExportingStateController.class);
    when(controller.getByTenant(tenantId)).thenReturn(byTenant);
    return controller;
  }

  private static ExportingStateController controllerFor(final Set<String> tenantIds) {
    final var controller = mock(ExportingStateController.class);
    tenantIds.forEach(id -> when(controller.getByTenant(id)).thenReturn(mock(ByTenant.class)));
    return controller;
  }

  @ParameterizedTest
  @ValueSource(strings = {ExportingEndpoint.PAUSE, ExportingEndpoint.RESUME})
  void pauseAndResumeFailsIfCallFailsDirectly(final String operation) {
    // given
    final var byTenant = mock(ByTenant.class);
    final var endpoint = new ExportingEndpoint(controllerFor(DEFAULT_PHYSICAL_TENANT_ID, byTenant));

    // when
    when(byTenant.pauseExporting()).thenThrow(new RuntimeException());
    when(byTenant.resumeExporting()).thenThrow(new RuntimeException());

    // then
    assertThat(endpoint.post(operation, false, null))
        .returns(
            WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR, from(WebEndpointResponse::getStatus));
  }

  @ParameterizedTest
  @ValueSource(strings = {ExportingEndpoint.PAUSE, ExportingEndpoint.RESUME})
  void pauseAndResumeFailIfCallReturnsFailedFuture(final String operation) {
    // given
    final var byTenant = mock(ByTenant.class);
    final var endpoint = new ExportingEndpoint(controllerFor(DEFAULT_PHYSICAL_TENANT_ID, byTenant));

    // when
    when(byTenant.pauseExporting())
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException()));
    when(byTenant.resumeExporting())
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException()));

    // then
    assertThat(endpoint.post(operation, false, null))
        .returns(
            WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR, from(WebEndpointResponse::getStatus));
  }

  @ParameterizedTest
  @ValueSource(strings = {ExportingEndpoint.PAUSE, ExportingEndpoint.RESUME})
  void pauseAndResumeCanSucceed(final String operation) {
    // given
    final var byTenant = mock(ByTenant.class);
    final var endpoint = new ExportingEndpoint(controllerFor(DEFAULT_PHYSICAL_TENANT_ID, byTenant));

    // when
    when(byTenant.pauseExporting()).thenReturn(CompletableFuture.completedFuture(null));
    when(byTenant.resumeExporting()).thenReturn(CompletableFuture.completedFuture(null));

    // then
    assertThat(endpoint.post(operation, false, null))
        .returns(WebEndpointResponse.STATUS_NO_CONTENT, from(WebEndpointResponse::getStatus));
  }

  @ParameterizedTest
  @MethodSource("exceptionSource")
  void shouldReturnResponseCorrectlyWhenExceptionIsThrown(
      final String operation, final String message) {
    // given
    final var byTenant = mock(ByTenant.class);
    final var endpoint = new ExportingEndpoint(controllerFor(DEFAULT_PHYSICAL_TENANT_ID, byTenant));
    final var exception = new RuntimeException(message);

    // when
    when(byTenant.pauseExporting()).thenReturn(CompletableFuture.failedFuture(exception));
    when(byTenant.resumeExporting())
        .thenReturn(CompletableFuture.failedFuture(new CompletionException(exception)));

    // then
    assertThat(endpoint.post(operation, false, null))
        .returns(
            WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR, from(WebEndpointResponse::getStatus))
        .satisfies(
            resp -> {
              if (message != null) {
                assertThat(resp.getBody())
                    .isInstanceOf(String.class)
                    .asString()
                    .contains(exception.getMessage());
              } else {
                assertThat(resp.getBody()).isNull();
              }
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {ExportingEndpoint.PAUSE, ExportingEndpoint.RESUME})
  void shouldApplyToEveryPhysicalTenantWithoutParameter(final String operation) {
    // given - the input set is deliberately reversed to prove the endpoint sorts rather than
    // relying on Set iteration order
    final var controller = controllerFor(Set.of("tenanta", "tenantb"));
    final var endpoint = new ExportingEndpoint(controller, () -> Set.of("tenantb", "tenanta"));
    final var byTenantA = controller.getByTenant("tenanta");
    final var byTenantB = controller.getByTenant("tenantb");
    when(byTenantA.pauseExporting()).thenReturn(CompletableFuture.completedFuture(null));
    when(byTenantA.resumeExporting()).thenReturn(CompletableFuture.completedFuture(null));
    when(byTenantB.pauseExporting()).thenReturn(CompletableFuture.completedFuture(null));
    when(byTenantB.resumeExporting()).thenReturn(CompletableFuture.completedFuture(null));
    clearInvocations(controller);

    // when
    final var response = endpoint.post(operation, false, null);

    // then
    assertThat(response.getStatus()).isEqualTo(WebEndpointResponse.STATUS_NO_CONTENT);
    for (final var tenantAccess : List.of(byTenantA, byTenantB)) {
      if (ExportingEndpoint.PAUSE.equals(operation)) {
        verify(tenantAccess).pauseExporting();
      } else {
        verify(tenantAccess).resumeExporting();
      }
    }
    verifyNoMoreInteractions(byTenantA, byTenantB);
    // the endpoint must process tenants in sorted order, not Set iteration order
    final var inOrderOnController = inOrder(controller);
    inOrderOnController.verify(controller).getByTenant("tenanta");
    inOrderOnController.verify(controller).getByTenant("tenantb");
  }

  @ParameterizedTest
  @ValueSource(strings = {ExportingEndpoint.PAUSE, ExportingEndpoint.RESUME})
  void shouldApplyOnlyToRequestedPhysicalTenant(final String operation) {
    // given
    final var controller = controllerFor(Set.of("tenanta", "tenantb"));
    final var endpoint = new ExportingEndpoint(controller, () -> Set.of("tenanta", "tenantb"));
    final var byTenantA = controller.getByTenant("tenanta");
    final var byTenantB = controller.getByTenant("tenantb");
    when(byTenantB.pauseExporting()).thenReturn(CompletableFuture.completedFuture(null));
    when(byTenantB.resumeExporting()).thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var response = endpoint.post(operation, false, "tenantb");

    // then
    assertThat(response.getStatus()).isEqualTo(WebEndpointResponse.STATUS_NO_CONTENT);
    if (ExportingEndpoint.PAUSE.equals(operation)) {
      verify(byTenantB).pauseExporting();
    } else {
      verify(byTenantB).resumeExporting();
    }
    verifyNoInteractions(byTenantA);
    verifyNoMoreInteractions(byTenantB);
  }

  @Test
  void shouldAttemptEveryPhysicalTenantWhenOneThrowsBeforeReturningAFuture() {
    // given a tenant whose change submission fails synchronously rather than through a failed
    // future
    final var controller = controllerFor(Set.of("tenanta", "tenantb"));
    final var endpoint = new ExportingEndpoint(controller, () -> Set.of("tenanta", "tenantb"));
    final var byTenantA = controller.getByTenant("tenanta");
    final var byTenantB = controller.getByTenant("tenantb");
    when(byTenantA.pauseExporting()).thenThrow(new RuntimeException("tenanta has no leader"));
    when(byTenantB.pauseExporting()).thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var response = endpoint.post(ExportingEndpoint.PAUSE, false, null);

    // then the remaining tenant is still attempted, so the operator is not left with a mixed
    // cluster that the response says nothing about
    assertThat(response.getStatus()).isEqualTo(WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).asString().contains("tenanta has no leader");
    verify(byTenantB).pauseExporting();
  }

  @Test
  void shouldAttemptEveryPhysicalTenantEvenWhenOneFails() {
    // given
    final var controller = controllerFor(Set.of("tenanta", "tenantb"));
    final var endpoint = new ExportingEndpoint(controller, () -> Set.of("tenanta", "tenantb"));
    final var byTenantA = controller.getByTenant("tenanta");
    final var byTenantB = controller.getByTenant("tenantb");
    when(byTenantA.pauseExporting())
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("tenanta is down")));
    when(byTenantB.pauseExporting()).thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var response = endpoint.post(ExportingEndpoint.PAUSE, false, null);

    // then
    assertThat(response.getStatus()).isEqualTo(WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).asString().contains("tenanta is down");
    verify(byTenantB).pauseExporting();
  }

  @Test
  void shouldRejectUnknownPhysicalTenant() {
    // given
    final var controller = controllerFor(Set.of("tenanta"));
    final var endpoint = new ExportingEndpoint(controller, () -> Set.of("tenanta"));

    // when
    final var response = endpoint.post(ExportingEndpoint.PAUSE, false, "tenantz");

    // then
    assertThat(response.getStatus()).isEqualTo(WebEndpointResponse.STATUS_BAD_REQUEST);
    assertThat(response.getBody())
        .asString()
        .isEqualTo("Unknown physical tenant 'tenantz'. Configured physical tenants: [tenanta].");
    verifyNoInteractions(controller);
  }

  @Test
  void shouldTreatBlankPhysicalTenantAsEveryTenant() {
    // given
    final var controller = controllerFor(Set.of("tenanta", "tenantb"));
    final var endpoint = new ExportingEndpoint(controller, () -> Set.of("tenanta", "tenantb"));
    final var byTenantA = controller.getByTenant("tenanta");
    final var byTenantB = controller.getByTenant("tenantb");
    when(byTenantA.pauseExporting()).thenReturn(CompletableFuture.completedFuture(null));
    when(byTenantB.pauseExporting()).thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var response = endpoint.post(ExportingEndpoint.PAUSE, false, "  ");

    // then
    assertThat(response.getStatus()).isEqualTo(WebEndpointResponse.STATUS_NO_CONTENT);
    verify(byTenantA).pauseExporting();
    verify(byTenantB).pauseExporting();
  }

  private static Stream<Arguments> exceptionSource() {
    return Stream.of(ExportingEndpoint.PAUSE, ExportingEndpoint.RESUME)
        .flatMap(
            operation ->
                Stream.of("expected error", null).map(str -> Arguments.of(operation, str)));
  }
}
