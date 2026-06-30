/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.identity;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.spring.context.holder.HttpSessionBasedAuthenticationHolder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

class SessionCamundaAuthenticationMaterializationInterceptorTest {

  private final SessionCamundaAuthenticationMaterializationInterceptor interceptor =
      new SessionCamundaAuthenticationMaterializationInterceptor();

  @Test
  void shouldSkipWhenNoSessionExists() {
    // given — request with no session
    final var request = new MockHttpServletRequest();

    // when/then — no exception
    assertThatNoException()
        .isThrownBy(
            () ->
                interceptor.afterCompletion(
                    request, new MockHttpServletResponse(), new Object(), null));
  }

  @Test
  void shouldSkipWhenSessionHasNoCamundaAuthentication() {
    // given — session without the authentication attribute
    final var session = new MockHttpSession();
    session.setAttribute("some.other.key", "some-value");
    final var request = new MockHttpServletRequest();
    request.setSession(session);

    // when/then — no exception
    assertThatNoException()
        .isThrownBy(
            () ->
                interceptor.afterCompletion(
                    request, new MockHttpServletResponse(), new Object(), null));
  }

  /**
   * Documents the root cause: if LazyList.writeReplace() calls a supplier that throws (e.g. because
   * PhysicalTenantContext.current() is unavailable after the request scope is cleared), Java
   * serialisation of CamundaAuthentication fails.
   */
  @Test
  void shouldDocumentThatSerializationFailsWhenLazySupplierThrows() {
    // given — lazy supplier that simulates PhysicalTenantContext.current() called post-request
    final Supplier<List<String>> throwingSupplier =
        () -> {
          throw new IllegalStateException(
              "PhysicalTenantContext.current() called outside a request scope");
        };
    final var auth =
        CamundaAuthentication.of(b -> b.user("alice").tenantsSupplier(throwingSupplier));

    // then — serialisation throws because LazyList.writeReplace() invokes the supplier
    assertThatThrownBy(() -> serialize(auth)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldMaterializeLazyListsBeforeSerializationHappens() throws Exception {
    // given — CamundaAuthentication with lazy suppliers that become unavailable after the
    // request scope is cleared (simulating PhysicalTenantContext.current() post-request)
    final var contextAvailable = new AtomicBoolean(true);
    final Supplier<List<String>> contextSensitiveSupplier =
        () -> {
          if (!contextAvailable.get()) {
            throw new IllegalStateException(
                "PhysicalTenantContext.current() called outside a request scope");
          }
          return List.of("tenant-1");
        };
    final var auth =
        CamundaAuthentication.of(
            b ->
                b.user("alice")
                    .groupIdsSupplier(contextSensitiveSupplier)
                    .roleIdsSupplier(contextSensitiveSupplier)
                    .tenantsSupplier(contextSensitiveSupplier)
                    .mappingRulesSupplier(contextSensitiveSupplier));

    final var session = new MockHttpSession();
    session.setAttribute(
        HttpSessionBasedAuthenticationHolder.CAMUNDA_AUTHENTICATION_SESSION_HOLDER_KEY, auth);
    final var request = new MockHttpServletRequest();
    request.setSession(session);

    // when — interceptor runs while context is still available (inside afterCompletion)
    interceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), null);

    // then — mark context as unavailable (simulates DispatcherServlet clearing
    // RequestContextHolder)
    contextAvailable.set(false);

    // Serialisation must succeed because LazyList is already materialised; the supplier is
    // not called again during writeReplace()
    assertThatNoException().isThrownBy(() -> serialize(auth));
  }

  private static byte[] serialize(final Object obj) throws IOException {
    final var baos = new ByteArrayOutputStream();
    try (final var oos = new ObjectOutputStream(baos)) {
      oos.writeObject(obj);
    }
    return baos.toByteArray();
  }
}
