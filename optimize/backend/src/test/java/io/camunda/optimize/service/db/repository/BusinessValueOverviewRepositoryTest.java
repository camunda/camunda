/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

import static io.camunda.optimize.service.db.DatabaseConstants.BUSINESS_VALUE_OVERVIEW_INDEX_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.core.BulkRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.AutomationRateBlock;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.CycleTimeBlock;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.MetricRange;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.repository.es.BusinessValueOverviewRepositoryES;
import io.camunda.optimize.service.db.repository.os.BusinessValueOverviewRepositoryOS;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.util.importing.ZeebeConstants;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BusinessValueOverviewRepositoryTest {

  /**
   * An empty id list reaches the repository whenever a definition had no overview rows, so the
   * common case must not cost a round trip. Asserting the client is untouched rather than that
   * nothing was deleted keeps an empty bulk request — accepted by both engines — from passing.
   */
  @Test
  void shouldNotIssueABulkRequestWhenThereAreNoIdsToDelete() {
    // given
    final OptimizeElasticsearchClient esClient = mock(OptimizeElasticsearchClient.class);
    final OptimizeOpenSearchClient osClient = mock(OptimizeOpenSearchClient.class);
    final OptimizeIndexNameService indexNameService = mock(OptimizeIndexNameService.class);

    // when -- null means "nothing to delete" rather than throwing, matching bulkUpsert
    final List<String> noIds = null;
    new BusinessValueOverviewRepositoryES(esClient, new ObjectMapper()).deleteByIds(noIds);
    new BusinessValueOverviewRepositoryOS(osClient, indexNameService).deleteByIds(noIds);
    new BusinessValueOverviewRepositoryES(esClient, new ObjectMapper()).deleteByIds(List.of());
    new BusinessValueOverviewRepositoryOS(osClient, indexNameService).deleteByIds(List.of());

    // then
    verifyNoInteractions(esClient);
    verifyNoInteractions(osClient);
  }

  /**
   * Covered per engine because the two implementations address the index differently — a prefixed
   * concrete index on Elasticsearch, a prefixed alias on OpenSearch — and a delete aimed at the
   * wrong index fails silently rather than throwing.
   */
  @Test
  void shouldDeleteEveryGivenIdFromTheOverviewIndexOnElasticsearch() {
    // given
    final OptimizeElasticsearchClient esClient = mock(OptimizeElasticsearchClient.class);
    when(esClient.addPrefixesToIndices(BUSINESS_VALUE_OVERVIEW_INDEX_NAME))
        .thenReturn(List.of("prefixed-" + BUSINESS_VALUE_OVERVIEW_INDEX_NAME));

    // when
    new BusinessValueOverviewRepositoryES(esClient, new ObjectMapper())
        .deleteByIds(List.of("tenant-a::process-1::7d", "tenant-a::process-1::30d"));

    // then
    final ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
    verify(esClient)
        .doBulkRequest(captor.capture(), eq(BUSINESS_VALUE_OVERVIEW_INDEX_NAME), eq(false));
    assertThat(captor.getValue().operations())
        .extracting(operation -> operation.delete().index(), operation -> operation.delete().id())
        .containsExactly(
            tuple("prefixed-" + BUSINESS_VALUE_OVERVIEW_INDEX_NAME, "tenant-a::process-1::7d"),
            tuple("prefixed-" + BUSINESS_VALUE_OVERVIEW_INDEX_NAME, "tenant-a::process-1::30d"));
  }

  @Test
  void shouldDeleteEveryGivenIdFromTheOverviewIndexOnOpenSearch() {
    // given
    final OptimizeOpenSearchClient osClient = mock(OptimizeOpenSearchClient.class);
    final OptimizeIndexNameService indexNameService = mock(OptimizeIndexNameService.class);
    when(indexNameService.getOptimizeIndexAliasForIndex(BUSINESS_VALUE_OVERVIEW_INDEX_NAME))
        .thenReturn("prefixed-" + BUSINESS_VALUE_OVERVIEW_INDEX_NAME);

    // when
    new BusinessValueOverviewRepositoryOS(osClient, indexNameService)
        .deleteByIds(List.of("tenant-a::process-1::7d", "tenant-a::process-1::30d"));

    // then
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<List<org.opensearch.client.opensearch.core.bulk.BulkOperation>> captor =
        ArgumentCaptor.forClass(List.class);
    verify(osClient)
        .doBulkRequest(any(), captor.capture(), eq(BUSINESS_VALUE_OVERVIEW_INDEX_NAME), eq(false));
    assertThat(captor.getValue())
        .extracting(operation -> operation.delete().index(), operation -> operation.delete().id())
        .containsExactly(
            tuple("prefixed-" + BUSINESS_VALUE_OVERVIEW_INDEX_NAME, "tenant-a::process-1::7d"),
            tuple("prefixed-" + BUSINESS_VALUE_OVERVIEW_INDEX_NAME, "tenant-a::process-1::30d"));
  }

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
