/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.interceptor;

import static io.camunda.search.connect.configuration.DatabaseType.ELASTICSEARCH;
import static io.camunda.search.connect.configuration.DatabaseType.NONE;
import static io.camunda.search.connect.configuration.DatabaseType.OPENSEARCH;
import static io.camunda.search.connect.configuration.DatabaseType.RDBMS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.camunda.cluster.SecondaryStorageReadiness;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.service.exception.SecondaryStorageDegradedException;
import io.camunda.service.exception.SecondaryStorageTypeNotSupportedException;
import io.camunda.service.exception.SecondaryStorageUnavailableException;
import io.camunda.spring.utils.PhysicalTenantContext;
import io.camunda.zeebe.gateway.rest.annotation.ClusterScoped;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;

class SecondaryStorageInterceptorTest {

  private static final String PHYSICAL_TENANT_ID = "tenanta";

  /** Handlers are built from real annotated classes so annotation lookup is not stubbed away. */
  private static final HandlerMethod UNANNOTATED = handlerMethodFor(new Unannotated());

  private static final HandlerMethod ANY_STORAGE = handlerMethodFor(new AnyStorage());
  private static final HandlerMethod DOCUMENT_STORAGE_ONLY =
      handlerMethodFor(new DocumentStorageOnly());
  private static final HandlerMethod METHOD_OVERRIDE = handlerMethodFor(new MethodOverride());
  private static final HandlerMethod CLUSTER_WIDE = handlerMethodFor(new ClusterWide());

  private HttpServletRequest request;
  private HttpServletResponse response;

  @BeforeEach
  void setUp() {
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
    when(request.getDispatcherType()).thenReturn(DispatcherType.REQUEST);
  }

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void shouldAllowWhenNoAnnotation() {
    final var interceptor = interceptor(ELASTICSEARCH, SecondaryStorageReadiness.ALWAYS_READY);

    final boolean result = interceptor.preHandle(request, response, UNANNOTATED);

    assertThat(result).isTrue();
  }

  @Test
  void shouldNotConsultReadinessWhenNoAnnotation() {
    final var readiness = mock(SecondaryStorageReadiness.class);
    final var interceptor = interceptor(ELASTICSEARCH, readiness);

    interceptor.preHandle(request, response, UNANNOTATED);

    verifyNoInteractions(readiness);
  }

  @Test
  void shouldAllowWhenSecondaryStorageEnabledAndTenantServiceable() {
    bindPhysicalTenant(PHYSICAL_TENANT_ID);
    final var interceptor = interceptor(ELASTICSEARCH, SecondaryStorageReadiness.ALWAYS_READY);

    final boolean result = interceptor.preHandle(request, response, ANY_STORAGE);

    assertThat(result).isTrue();
  }

  @Test
  void shouldThrowWhenSecondaryStorageDisabledAndAnnotationPresent() {
    bindPhysicalTenant(PHYSICAL_TENANT_ID);
    final var interceptor = interceptor(NONE, SecondaryStorageReadiness.ALWAYS_READY);

    assertThatThrownBy(() -> interceptor.preHandle(request, response, ANY_STORAGE))
        .isInstanceOf(SecondaryStorageUnavailableException.class);
  }

  @Test
  void shouldRejectWithForbiddenEvenWhenTenantDegradedAndSecondaryStorageDisabled() {
    bindPhysicalTenant(PHYSICAL_TENANT_ID);
    final var interceptor = interceptor(NONE, degraded(PHYSICAL_TENANT_ID));

    assertThatThrownBy(() -> interceptor.preHandle(request, response, ANY_STORAGE))
        .isInstanceOf(SecondaryStorageUnavailableException.class);
  }

  @Test
  void shouldThrowWhenPhysicalTenantDegraded() {
    bindPhysicalTenant(PHYSICAL_TENANT_ID);
    final var interceptor = interceptor(ELASTICSEARCH, degraded(PHYSICAL_TENANT_ID));

    assertThatThrownBy(() -> interceptor.preHandle(request, response, ANY_STORAGE))
        .isInstanceOf(SecondaryStorageDegradedException.class);
  }

  @Test
  void shouldHintRetryAfterWhenPhysicalTenantDegraded() {
    bindPhysicalTenant(PHYSICAL_TENANT_ID);
    final var interceptor = interceptor(ELASTICSEARCH, degraded(PHYSICAL_TENANT_ID));

    assertThatThrownBy(() -> interceptor.preHandle(request, response, ANY_STORAGE))
        .isInstanceOf(SecondaryStorageDegradedException.class);

    verify(response).setHeader(HttpHeaders.RETRY_AFTER, "5");
  }

  @Test
  void shouldNotHintRetryAfterWhenSecondaryStorageDisabled() {
    bindPhysicalTenant(PHYSICAL_TENANT_ID);
    final var interceptor = interceptor(NONE, SecondaryStorageReadiness.ALWAYS_READY);

    assertThatThrownBy(() -> interceptor.preHandle(request, response, ANY_STORAGE))
        .isInstanceOf(SecondaryStorageUnavailableException.class);

    verifyNoInteractions(response);
  }

  @Test
  void shouldSkipDegradedCheckOnAsyncDispatch() {
    when(request.getDispatcherType()).thenReturn(DispatcherType.ASYNC);
    bindPhysicalTenant(PHYSICAL_TENANT_ID);
    final var interceptor = interceptor(ELASTICSEARCH, degraded(PHYSICAL_TENANT_ID));

    final boolean result = interceptor.preHandle(request, response, ANY_STORAGE);

    assertThat(result).isTrue();
  }

  @Test
  void shouldAllowWhenTenantStorageIsDeclared() {
    bindPhysicalTenant(PHYSICAL_TENANT_ID);
    final var interceptor = interceptor(OPENSEARCH, SecondaryStorageReadiness.ALWAYS_READY);

    final boolean result = interceptor.preHandle(request, response, DOCUMENT_STORAGE_ONLY);

    assertThat(result).isTrue();
  }

  @Test
  void shouldThrowWhenTenantStorageIsNotDeclared() {
    bindPhysicalTenant(PHYSICAL_TENANT_ID);
    final var interceptor = interceptor(RDBMS, SecondaryStorageReadiness.ALWAYS_READY);

    assertThatThrownBy(() -> interceptor.preHandle(request, response, DOCUMENT_STORAGE_ONLY))
        .isInstanceOf(SecondaryStorageTypeNotSupportedException.class)
        .hasMessageContaining("elasticsearch, opensearch")
        .hasMessageContaining("'rdbms'");
  }

  /**
   * A tenant with no secondary storage is reported as unavailable rather than as an unsupported
   * type, even on an endpoint that declares a set: "none" is the absence of storage, not a storage
   * the endpoint could have supported.
   */
  @Test
  void shouldReportNoSecondaryStorageAsUnavailableRatherThanUnsupportedType() {
    bindPhysicalTenant(PHYSICAL_TENANT_ID);
    final var interceptor = interceptor(NONE, SecondaryStorageReadiness.ALWAYS_READY);

    assertThatThrownBy(() -> interceptor.preHandle(request, response, DOCUMENT_STORAGE_ONLY))
        .isInstanceOf(SecondaryStorageUnavailableException.class);
  }

  @Test
  void shouldPreferMethodAnnotationOverClassAnnotation() {
    bindPhysicalTenant(PHYSICAL_TENANT_ID);
    final var interceptor = interceptor(RDBMS, SecondaryStorageReadiness.ALWAYS_READY);

    // the class declares ELASTICSEARCH, the method declares RDBMS — the method wins
    final boolean result = interceptor.preHandle(request, response, METHOD_OVERRIDE);

    assertThat(result).isTrue();
  }

  /**
   * A cluster-wide endpoint is not served on behalf of one physical tenant, so gating it on the
   * request's own — the default one, for an unstamped {@code /cluster/v2} request — would let one
   * arbitrary tenant decide whether the whole cluster-wide surface answers.
   */
  @Test
  void shouldAllowClusterScopedEndpointWhenOnlyAnotherPhysicalTenantIsReady() {
    bindPhysicalTenant(PHYSICAL_TENANT_ID);
    final var readiness = mock(SecondaryStorageReadiness.class);
    when(readiness.isReady(PHYSICAL_TENANT_ID)).thenReturn(false);
    when(readiness.anyReady()).thenReturn(true);
    final var interceptor = interceptor(ELASTICSEARCH, readiness);

    final boolean result = interceptor.preHandle(request, response, CLUSTER_WIDE);

    assertThat(result).isTrue();
  }

  @Test
  void shouldThrowOnClusterScopedEndpointWhenNoPhysicalTenantIsReady() {
    bindPhysicalTenant(PHYSICAL_TENANT_ID);
    final var readiness = mock(SecondaryStorageReadiness.class);
    when(readiness.anyReady()).thenReturn(false);
    final var interceptor = interceptor(ELASTICSEARCH, readiness);

    assertThatThrownBy(() -> interceptor.preHandle(request, response, CLUSTER_WIDE))
        .isInstanceOf(SecondaryStorageDegradedException.class)
        .hasMessage(SecondaryStorageDegradedException.CLUSTER_SECONDARY_STORAGE_DEGRADED_MESSAGE);
  }

  @Test
  void shouldNotConsultThePerTenantReadinessOnAClusterScopedEndpoint() {
    bindPhysicalTenant(PHYSICAL_TENANT_ID);
    final var readiness = mock(SecondaryStorageReadiness.class);
    when(readiness.anyReady()).thenReturn(true);
    final var interceptor = interceptor(ELASTICSEARCH, readiness);

    interceptor.preHandle(request, response, CLUSTER_WIDE);

    verify(readiness, never()).isReady(any());
  }

  private static SecondaryStorageInterceptor interceptor(
      final DatabaseType databaseType, final SecondaryStorageReadiness readiness) {
    return new SecondaryStorageInterceptor(tenantId -> databaseType, readiness);
  }

  private static HandlerMethod handlerMethodFor(final Object controller) {
    try {
      return new HandlerMethod(controller, controller.getClass().getMethod("handle"));
    } catch (final NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void bindPhysicalTenant(final String physicalTenantId) {
    final var mockRequest = mock(HttpServletRequest.class);
    when(mockRequest.getAttribute(PhysicalTenantContext.REQUEST_ATTRIBUTE_PHYSICAL_TENANT_ID))
        .thenReturn(physicalTenantId);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));
  }

  private static SecondaryStorageReadiness degraded(final String physicalTenantId) {
    final var readiness = mock(SecondaryStorageReadiness.class);
    when(readiness.isReady(physicalTenantId)).thenReturn(false);
    return readiness;
  }

  public static class Unannotated {
    public String handle() {
      return "ok";
    }
  }

  @RequiresSecondaryStorage
  public static class AnyStorage {
    public String handle() {
      return "ok";
    }
  }

  @RequiresSecondaryStorage({ELASTICSEARCH, OPENSEARCH})
  public static class DocumentStorageOnly {
    public String handle() {
      return "ok";
    }
  }

  @ClusterScoped
  @RequiresSecondaryStorage({ELASTICSEARCH, OPENSEARCH})
  public static class ClusterWide {
    public String handle() {
      return "ok";
    }
  }

  @RequiresSecondaryStorage(ELASTICSEARCH)
  public static class MethodOverride {
    @RequiresSecondaryStorage(RDBMS)
    public String handle() {
      return "ok";
    }
  }
}
