/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.event.CamundaActivityEventDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.events.CamundaActivityEventIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.camunda.optimize.service.es.schema.index.events.CamundaActivityEventIndex.PROCESS_DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.index.events.CamundaActivityEventIndex.TENANT_ID;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.search.sort.SortOrder.ASC;

@AllArgsConstructor
@Component
@Slf4j
public class CamundaActivityEventReader {
  private final ObjectMapper objectMapper;
  private final OptimizeElasticsearchClient esClient;
  private final DateTimeFormatter formatter;

  public List<CamundaActivityEventDto> getCamundaActivityEventsForDefinitionAfter(final String definitionKey,
                                                                                  final Long eventTimestamp,
                                                                                  final int limit) {
    log.debug(
      "Fetching camunda activity events for key [{}] and with timestamp after {}", definitionKey, eventTimestamp
    );

    final RangeQueryBuilder timestampQuery = rangeQuery(CamundaActivityEventIndex.TIMESTAMP)
      .gt(formatter.format(convertToOffsetDateTime(eventTimestamp)));

    return getPageOfEventsForDefinitionKeySortedByTimestamp(definitionKey, timestampQuery, limit);
  }

  public List<CamundaActivityEventDto> getCamundaActivityEventsForDefinitionAt(final String definitionKey,
                                                                               final Long eventTimestamp) {
    log.debug(
      "Fetching camunda activity events for key [{}] and with exact timestamp {}.", definitionKey, eventTimestamp
    );

    final RangeQueryBuilder timestampQuery = rangeQuery(CamundaActivityEventIndex.TIMESTAMP)
      .lte(formatter.format(convertToOffsetDateTime(eventTimestamp)))
      .gte(formatter.format(convertToOffsetDateTime(eventTimestamp)));

    return getPageOfEventsForDefinitionKeySortedByTimestamp(definitionKey, timestampQuery, MAX_RESPONSE_SIZE_LIMIT);
  }

  public List<CamundaActivityEventDto> getCamundaActivityEventsForDefinitionWithVersionAndTenantAfter(
    final String definitionKey,
    final List<String> versions,
    final List<String> tenantIds,
    final Long eventTimestamp,
    final int limit) {
    log.debug(
      "Fetching camunda activity events for key [{}] with versions [{}], tenant IDs [{}] and with timestamp after {}",
      definitionKey, versions, tenantIds, eventTimestamp
    );

    final BoolQueryBuilder eventsQuery = buildQueryForVersionsAndTenants(versions, tenantIds)
      .must(rangeQuery(CamundaActivityEventIndex.TIMESTAMP)
              .gt(formatter.format(convertToOffsetDateTime(eventTimestamp))));

    return getPageOfEventsForDefinitionKeySortedByTimestamp(definitionKey, eventsQuery, limit);
  }

  public List<CamundaActivityEventDto> getCamundaActivityEventsForDefinitionWithVersionAndTenantAt(
    final String definitionKey,
    final List<String> versions,
    final List<String> tenantIds,
    final Long eventTimestamp) {
    log.debug(
      "Fetching camunda activity events for key [{}] with versions [{}], tenant IDs [{}] and with timestamp at {}",
      definitionKey, versions, tenantIds, eventTimestamp
    );

    final BoolQueryBuilder eventsQuery = buildQueryForVersionsAndTenants(versions, tenantIds)
      .must(rangeQuery(CamundaActivityEventIndex.TIMESTAMP)
              .lte(formatter.format(convertToOffsetDateTime(eventTimestamp)))
              .gte(formatter.format(convertToOffsetDateTime(eventTimestamp))));

    return getPageOfEventsForDefinitionKeySortedByTimestamp(definitionKey, eventsQuery, MAX_RESPONSE_SIZE_LIMIT);
  }

  private BoolQueryBuilder buildQueryForVersionsAndTenants(final List<String> versions, final List<String> tenantIds) {
    final BoolQueryBuilder eventsQuery = boolQuery();
    List<String> tenantsToFilterFor = new ArrayList<>(tenantIds);
    boolean includeEmptyTenant = tenantsToFilterFor.contains(null);
    tenantsToFilterFor.removeIf(Objects::isNull);
    if (tenantsToFilterFor.isEmpty() || (tenantsToFilterFor.size() == 1 && includeEmptyTenant)) {
      eventsQuery.mustNot(existsQuery(TENANT_ID));
    } else {
      eventsQuery.should(termsQuery(TENANT_ID, tenantsToFilterFor));
      if (includeEmptyTenant) {
        eventsQuery.should(boolQuery().mustNot(existsQuery(TENANT_ID)));
      }
    }

    boolean useAllVersions = versions != null && versions.stream()
      .filter(Objects::nonNull)
      .anyMatch(version -> version.equalsIgnoreCase(ReportConstants.ALL_VERSIONS));
    if (versions != null && !useAllVersions) {
      versions.removeIf(Objects::isNull);
      eventsQuery.must(termsQuery(PROCESS_DEFINITION_VERSION, versions));
    }
    return eventsQuery;
  }

  private OffsetDateTime convertToOffsetDateTime(final Long eventTimestamp) {
    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(eventTimestamp), ZoneId.systemDefault());
  }

  private List<CamundaActivityEventDto> getPageOfEventsForDefinitionKeySortedByTimestamp(final String definitionKey,
                                                                                         final QueryBuilder query,
                                                                                         final int limit) {
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .sort(SortBuilders.fieldSort(CamundaActivityEventIndex.TIMESTAMP).order(ASC))
      .size(limit);

    final SearchRequest searchRequest =
      new SearchRequest(new CamundaActivityEventIndex(definitionKey).getIndexName())
        .source(searchSourceBuilder);

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return ElasticsearchHelper.mapHits(searchResponse.getHits(), CamundaActivityEventDto.class, objectMapper);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Was not able to retrieve camunda activity events!", e);
    }
  }
}
