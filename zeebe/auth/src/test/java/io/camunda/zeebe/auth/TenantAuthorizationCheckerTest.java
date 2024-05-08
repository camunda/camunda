/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.auth.api.TenantAuthorizationChecker;
import io.camunda.zeebe.auth.impl.TenantAuthorizationCheckerImpl;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TenantAuthorizationCheckerTest {

  private static TenantAuthorizationChecker accessChecker;

  @BeforeAll
  public static void setUp() {
    accessChecker = new TenantAuthorizationCheckerImpl(List.of("tenant-1", "tenant-2", "tenant-3"));
  }

  @Test
  public void shouldConfirmSingleTenantAccess() {
    // given
    final String tenantId = "tenant-1";

    // then
    assertThat(accessChecker.isAuthorized(tenantId)).isTrue();
  }

  @Test
  public void shouldRejectUnauthorizedTenant() {
    // given
    final String falseTenantId = "false-tenant";

    // then
    assertThat(accessChecker.isAuthorized(falseTenantId)).isFalse();
  }

  @Test
  public void shouldConfirmMultiTenantAccess() {
    // given
    final List<String> tenantIds = List.of("tenant-1", "tenant-2");

    // then
    assertThat(accessChecker.isFullyAuthorized(tenantIds)).isTrue();
  }

  @Test
  public void shouldRejectMultiTenantAccessWithUnauthorizedTenant() {
    // given
    final List<String> tenantIds = List.of("tenant-1", "tenant-2", "false-tenant");

    // then
    assertThat(accessChecker.isFullyAuthorized(tenantIds)).isFalse();
  }
}
