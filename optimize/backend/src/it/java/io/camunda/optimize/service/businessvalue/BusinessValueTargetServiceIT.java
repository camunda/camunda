/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.businessvalue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueTargetResponseDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueTargetUpsertRequestDto;
import io.camunda.optimize.rest.exceptions.ForbiddenException;
import io.camunda.optimize.rest.exceptions.NotFoundException;
import io.camunda.optimize.service.util.importing.ZeebeConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end verification for the paths through {@link BusinessValueTargetService} that touch the
 * real ES/OS backing: the empty-state read, the tenant-authorization gate on both endpoints, and
 * the 404 guard when the referenced definition does not exist. The upsert-plus-recompute round trip
 * runs against seeded reports from {@code BusinessValueDashboardService} and is exercised by {@code
 * BusinessValueOverviewComputeIT} on the sibling M2.4 branch; the pure orchestration (writer +
 * compute + response mapping) is covered by {@link BusinessValueTargetServiceTest}.
 */
class BusinessValueTargetServiceIT extends AbstractBrokerlessZeebeCCSMIT {

  private static final String USER = "sherrin@camunda.com";
  private static final String DEFAULT_TENANT = ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;

  private BusinessValueTargetService targetService;

  @BeforeEach
  void setUp() {
    targetService = embeddedOptimizeExtension.getBean(BusinessValueTargetService.class);
  }

  @Test
  void shouldReturnEmptyResponseWhenNoTargetExists() {
    // given a process key with no target ever set — no definition either, but the read path does
    // not gate on definition existence (that guard only fires on upsert; see §5.7 in the design)
    final String processKey = "no-target-" + System.nanoTime();

    // when
    final BusinessValueTargetResponseDto response =
        targetService.readTarget(USER, DEFAULT_TENANT, processKey);

    // then — the empty-state contract: all target fields null, path echo populated
    assertThat(response.tenantId()).isEqualTo(DEFAULT_TENANT);
    assertThat(response.processKey()).isEqualTo(processKey);
    assertThat(response.cycleTimeTarget()).isNull();
    assertThat(response.automationRateTargetPct()).isNull();
    assertThat(response.updatedAt()).isNull();
    assertThat(response.updatedBy()).isNull();
  }

  @Test
  void shouldReturnNotFoundWhenUpsertReferencesUnknownDefinition() {
    // The 404 guard prevents an authorized caller from writing an arbitrary processDefinitionKey
    // that would then materialize a fake process on every /overview response.
    final String ghostKey = "ghost-definition-" + System.nanoTime();

    assertThatThrownBy(
            () ->
                targetService.upsertTarget(
                    USER,
                    DEFAULT_TENANT,
                    ghostKey,
                    new BusinessValueTargetUpsertRequestDto(null, 50)))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void shouldForbidReadForUnauthorizedTenant() {
    // given a tenant no IT user is authorized to see
    final String forbiddenTenant = "unauthorized-" + System.nanoTime();

    // when + then
    assertThatThrownBy(() -> targetService.readTarget(USER, forbiddenTenant, "any-process"))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void shouldForbidUpsertForUnauthorizedTenant() {
    final String forbiddenTenant = "unauthorized-" + System.nanoTime();

    assertThatThrownBy(
            () ->
                targetService.upsertTarget(
                    USER,
                    forbiddenTenant,
                    "any-process",
                    new BusinessValueTargetUpsertRequestDto(null, 50)))
        .isInstanceOf(ForbiddenException.class);
  }
}
