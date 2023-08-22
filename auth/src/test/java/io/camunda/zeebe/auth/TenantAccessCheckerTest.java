/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.auth.api.TenantAccessChecker;
import io.camunda.zeebe.auth.impl.TenantAccessCheckerImpl;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class TenantAccessCheckerTest {

  private TenantAccessChecker accessChecker;

  @Before
  public void setUp() {
    accessChecker = new TenantAccessCheckerImpl(List.of("tenant-1", "tenant-2", "tenant-3"));
  }

  @Test
  public void shouldConfirmSingleTenantAccess() {
    // given
    final String tenantId = "tenant-1";

    // then
    assertThat(accessChecker.hasAccess(tenantId)).isTrue();
  }

  @Test
  public void shouldRejectUnauthorizedTenant() {
    // given
    final String falseTenantId = "false-tenant";

    // then
    assertThat(accessChecker.hasAccess(falseTenantId)).isFalse();
  }

  @Test
  public void shouldConfirmMultiTenantAccess() {
    // given
    final List<String> tenantIds = List.of("tenant-1", "tenant-2");

    // then
    assertThat(accessChecker.hasFullAccess(tenantIds)).isTrue();
  }

  @Test
  public void shouldRejectMultiTenantAccessWithUnauthorizedTenant() {
    // given
    final List<String> tenantIds = List.of("tenant-1", "tenant-2", "false-tenant");

    // then
    assertThat(accessChecker.hasFullAccess(tenantIds)).isFalse();
  }
}
