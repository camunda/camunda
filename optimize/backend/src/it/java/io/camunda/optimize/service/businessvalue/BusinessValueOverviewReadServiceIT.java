/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.businessvalue;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.AutomationRateBlock;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.CycleTimeBlock;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.MetricRange;
import io.camunda.optimize.service.db.repository.BusinessValueOverviewRepository;
import io.camunda.optimize.service.util.importing.ZeebeConstants;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the {@link BusinessValueOverviewRepository} read path against a live ES or OS backend.
 * Covers the {@code readByRange(range, tenantIds)} pushdown that keeps the response bounded even
 * when the cluster-wide row count would otherwise exceed the fetch cap. Assembly logic on top of
 * the repository is exhaustively covered by {@code BusinessValueOverviewReadServiceTest}, which is
 * cheaper to run and doesn't need a real backend.
 */
class BusinessValueOverviewReadServiceIT extends AbstractBrokerlessZeebeCCSMIT {

  private static final String DEFAULT_TENANT = ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;
  private static final String OTHER_TENANT = "tenant-b";

  private BusinessValueOverviewRepository overviewRepository;

  @BeforeEach
  void setUp() {
    overviewRepository = embeddedOptimizeExtension.getBean(BusinessValueOverviewRepository.class);
  }

  @Test
  void shouldReturnAllRowsForRangeWhenTenantFilterIsNull() {
    // given two rows in the same range under different tenants
    final BusinessValueOverviewDto rowA = fresh(DEFAULT_TENANT, "proc-a", MetricRange.THIRTY_DAYS);
    final BusinessValueOverviewDto rowB = fresh(OTHER_TENANT, "proc-b", MetricRange.THIRTY_DAYS);
    overviewRepository.bulkUpsert(List.of(rowA, rowB), true);

    // when — passing null bypasses the tenant filter; reserved for internal, tenant-agnostic
    // callers
    final List<BusinessValueOverviewDto> read =
        overviewRepository.readByRange(MetricRange.THIRTY_DAYS, null);

    // then
    assertThat(read)
        .extracting(BusinessValueOverviewDto::getProcessDefinitionKey)
        .contains("proc-a", "proc-b");
  }

  @Test
  void shouldReturnEmptyListWhenTenantFilterIsEmpty() {
    // given rows exist for the range
    overviewRepository.bulkUpsert(
        List.of(fresh(DEFAULT_TENANT, "proc-empty-check", MetricRange.THIRTY_DAYS)), true);

    // when — an empty collection is the shortcut for "caller sees no tenants"
    final List<BusinessValueOverviewDto> read =
        overviewRepository.readByRange(MetricRange.THIRTY_DAYS, Set.of());

    // then
    assertThat(read).isEmpty();
  }

  @Test
  void shouldPushDownTenantFilterAndOnlyReturnMatchingRows() {
    // given rows in the same range but two different tenants; the read must not return the
    // unauthorized tenant's row regardless of how many rows exist cluster-wide
    final BusinessValueOverviewDto authorized =
        fresh(DEFAULT_TENANT, "proc-authorized-1", MetricRange.THIRTY_DAYS);
    final BusinessValueOverviewDto unauthorized =
        fresh(OTHER_TENANT, "proc-unauthorized", MetricRange.THIRTY_DAYS);
    overviewRepository.bulkUpsert(List.of(authorized, unauthorized), true);

    // when
    final List<BusinessValueOverviewDto> read =
        overviewRepository.readByRange(MetricRange.THIRTY_DAYS, Set.of(DEFAULT_TENANT));

    // then
    assertThat(read)
        .extracting(BusinessValueOverviewDto::getProcessDefinitionKey)
        .contains("proc-authorized-1")
        .doesNotContain("proc-unauthorized");
    assertThat(read).allSatisfy(row -> assertThat(row.getTenantId()).isEqualTo(DEFAULT_TENANT));
  }

  @Test
  void shouldIsolateRowsByMetricRange() {
    // given rows across all four ranges for the same tenant/process
    overviewRepository.bulkUpsert(
        List.of(
            fresh(DEFAULT_TENANT, "proc-range-isolation", MetricRange.SEVEN_DAYS),
            fresh(DEFAULT_TENANT, "proc-range-isolation", MetricRange.THIRTY_DAYS),
            fresh(DEFAULT_TENANT, "proc-range-isolation", MetricRange.THREE_MONTHS),
            fresh(DEFAULT_TENANT, "proc-range-isolation", MetricRange.SIX_MONTHS)),
        true);

    // when
    final List<BusinessValueOverviewDto> read =
        overviewRepository.readByRange(MetricRange.THIRTY_DAYS, Set.of(DEFAULT_TENANT));

    // then — only the 30d row for our test process is returned; other ranges are excluded
    assertThat(read)
        .filteredOn(row -> "proc-range-isolation".equals(row.getProcessDefinitionKey()))
        .hasSize(1)
        .allSatisfy(row -> assertThat(row.getMetricRange()).isEqualTo(MetricRange.THIRTY_DAYS));
  }

  private static BusinessValueOverviewDto fresh(
      final String tenantId, final String processKey, final MetricRange range) {
    return new BusinessValueOverviewDto(
        tenantId,
        processKey,
        processKey,
        range,
        OffsetDateTime.now(ZoneOffset.UTC),
        new CycleTimeBlock(null, null, null),
        new AutomationRateBlock(null, null, null),
        false,
        0,
        0);
  }
}
