/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.AutomationRateBlock;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.CycleTimeBlock;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.MetricRange;
import io.camunda.optimize.service.util.importing.ZeebeConstants;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class BusinessValueOverviewRepositoryTest {

  @Test
  void shouldCombineTenantProcessKeyAndRangeIntoDocumentId() {
    assertThat(
            BusinessValueOverviewRepository.documentId(
                ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID,
                "invoice-automation",
                MetricRange.THIRTY_DAYS))
        .isEqualTo(ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID + "::invoice-automation::30d");
  }

  @Test
  void shouldReturnDifferentDocumentIdsForDifferentRanges() {
    final String a =
        BusinessValueOverviewRepository.documentId(
            ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID, "invoice-automation", MetricRange.SEVEN_DAYS);
    final String b =
        BusinessValueOverviewRepository.documentId(
            ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID, "invoice-automation", MetricRange.THIRTY_DAYS);
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void shouldReturnDifferentDocumentIdsForDifferentTenants() {
    final String a =
        BusinessValueOverviewRepository.documentId(
            "tenant-a", "invoice-automation", MetricRange.THIRTY_DAYS);
    final String b =
        BusinessValueOverviewRepository.documentId(
            "tenant-b", "invoice-automation", MetricRange.THIRTY_DAYS);
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void shouldRejectNullTenant() {
    assertThatThrownBy(
            () ->
                BusinessValueOverviewRepository.documentId(
                    null, "any-key", MetricRange.THIRTY_DAYS))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId");
  }

  @Test
  void shouldRejectNullProcessKey() {
    assertThatThrownBy(
            () ->
                BusinessValueOverviewRepository.documentId(
                    ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID, null, MetricRange.THIRTY_DAYS))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("processDefinitionKey");
  }

  @Test
  void shouldRejectNullRange() {
    assertThatThrownBy(
            () ->
                BusinessValueOverviewRepository.documentId(
                    ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID, "any-key", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("metricRange");
  }

  @Test
  void shouldMapEachMetricRangeIdToItsEnumConstant() {
    assertThat(MetricRange.fromId("7d")).isEqualTo(MetricRange.SEVEN_DAYS);
    assertThat(MetricRange.fromId("30d")).isEqualTo(MetricRange.THIRTY_DAYS);
    assertThat(MetricRange.fromId("3m")).isEqualTo(MetricRange.THREE_MONTHS);
    assertThat(MetricRange.fromId("6m")).isEqualTo(MetricRange.SIX_MONTHS);
  }

  @Test
  void shouldRejectUnknownMetricRangeId() {
    assertThatThrownBy(() -> MetricRange.fromId("12m"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("12m");
  }

  @Test
  void shouldSerializeMetricRangeAsIdString() throws Exception {
    // guards the ES storage/query alignment: docs are stored with the id string, term queries
    // filter on it
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    final BusinessValueOverviewDto dto =
        new BusinessValueOverviewDto(
            ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID,
            "invoice-automation",
            "Invoice Automation",
            MetricRange.THIRTY_DAYS,
            OffsetDateTime.parse("2026-08-05T04:00:15Z"),
            new CycleTimeBlock(1L, 2L, true),
            new AutomationRateBlock(72.4, 85, false),
            true,
            2,
            1);

    final String json = mapper.writeValueAsString(dto);

    assertThat(json).contains("\"metricRange\":\"30d\"");
    assertThat(json).doesNotContain("THIRTY_DAYS");

    final BusinessValueOverviewDto roundTripped =
        mapper.readValue(json, BusinessValueOverviewDto.class);
    assertThat(roundTripped.getMetricRange()).isEqualTo(MetricRange.THIRTY_DAYS);
  }
}
