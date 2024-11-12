/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.interceptors.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.interceptors.InterceptorUtil;
import io.camunda.zeebe.gateway.interceptors.util.TestTenantProvidingInterceptor;
import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.Status;
import org.assertj.core.api.ListAssert;
import org.junit.jupiter.api.Test;

public class CustomTenantProvidingInterceptorTest {

  @Test
  public void addsAuthorizedTenantsToContext() {
    // given
    final var interceptor = new TestTenantProvidingInterceptor();

    // when / then
    interceptor.interceptCall(
        new NoopServerCall<>() {},
        new Metadata(),
        (call, headers) -> {
          // then
          assertAuthorizedTenants()
              .describedAs("Expect that the authorized tenants is stored in the current Context")
              .contains("tenant-1");
          call.close(Status.OK, headers);
          return null;
        });
  }

  private static ListAssert<String> assertAuthorizedTenants() {
    try {
      return assertThat(
          Context.current().call(() -> InterceptorUtil.getAuthorizedTenantsKey().get()));
    } catch (final Exception e) {
      throw new RuntimeException("Unable to retrieve authorized tenants from context", e);
    }
  }
}
