/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import static io.camunda.webapps.schema.descriptors.template.OperationTemplate.ID;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationRequestDto;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class BatchOperationReader implements io.camunda.operate.webapp.reader.BatchOperationReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(BatchOperationReader.class);

  @Autowired private BatchOperationTemplate batchOperationTemplate;

  @Autowired private ElasticsearchClient es8Client;

  @Autowired private PermissionsService permissionsService;

  @Autowired
  @Qualifier("operateObjectMapper")
  private ObjectMapper objectMapper;

  @Override
  public List<BatchOperationEntity> getBatchOperations(
      final BatchOperationRequestDto batchOperationRequestDto) {

    final var searchRequest = createSearchRequest(batchOperationRequestDto);
    try {
      final var res = es8Client.search(searchRequest, BatchOperationEntity.class);
      final var batchOperationEntities =
          res.hits().hits().stream()
              .map(
                  hit -> {
                    final var src = hit.source();
                    src.setSortValues(hit.sort().stream().map(FieldValue::_get).toArray());
                    return src;
                  })
              .toList();

      if (batchOperationRequestDto.getSearchBefore() != null) {
        return ImmutableList.copyOf(batchOperationEntities).reverse();
      }

      return batchOperationEntities;
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while getting page of batch operations list: %s",
              e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private SearchRequest createSearchRequest(
      final BatchOperationRequestDto batchOperationRequestDto) {

    final SortOptions sort1, sort2;

    final var searchAfter = batchOperationRequestDto.getSearchAfter(objectMapper);
    final var searchBefore = batchOperationRequestDto.getSearchBefore(objectMapper);

    final Object[] querySearchAfter;

    if (searchAfter != null
        || searchBefore == null) { // this sorting is also the default one for 1st page
      sort1 =
          ElasticsearchUtil.sortOrder(
              BatchOperationTemplate.END_DATE,
              co.elastic.clients.elasticsearch._types.SortOrder.Desc,
              "_first");
      sort2 =
          ElasticsearchUtil.sortOrder(
              BatchOperationTemplate.START_DATE,
              co.elastic.clients.elasticsearch._types.SortOrder.Desc);
      querySearchAfter = searchAfter; // may be null
    } else { // searchBefore != null
      // reverse sorting
      sort1 =
          ElasticsearchUtil.sortOrder(
              BatchOperationTemplate.END_DATE,
              co.elastic.clients.elasticsearch._types.SortOrder.Asc,
              "_last");
      sort2 =
          ElasticsearchUtil.sortOrder(
              BatchOperationTemplate.START_DATE,
              co.elastic.clients.elasticsearch._types.SortOrder.Asc);
      querySearchAfter = searchBefore;
    }

    final var query = ElasticsearchUtil.constantScoreQuery(allowedOperationsQuery());
    final var searchReq =
        new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
            .query(query)
            .sort(sort1, sort2)
            .size(batchOperationRequestDto.getPageSize())
            .index(batchOperationTemplate.getAlias());

    if (querySearchAfter != null) {
      searchReq.searchAfter(Arrays.stream(querySearchAfter).map(FieldValue::of).toList());
    }

    return searchReq.build();
  }

  private Query allowedOperationsQuery() {
    final var allowed = permissionsService.getBatchOperationsWithPermission(PermissionType.READ);
    return allowed.isAll()
        ? Query.of(q -> q.matchAll(m -> m))
        : ElasticsearchUtil.termsQuery(ID, allowed.getIds());
  }
}
