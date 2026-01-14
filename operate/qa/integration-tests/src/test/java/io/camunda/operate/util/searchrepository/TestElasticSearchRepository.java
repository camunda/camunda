/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util.searchrepository;

import static io.camunda.operate.util.CollectionUtil.toSafeArrayOfStrings;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticsearchCondition.class)
public class TestElasticSearchRepository implements TestSearchRepository {
  @Autowired private ElasticsearchClient es8Client;

  @Autowired
  @Qualifier("operateObjectMapper")
  private ObjectMapper objectMapper;

  @Override
  public boolean createOrUpdateDocumentFromObject(
      final String indexName, final String docId, final Object data) throws IOException {
    final Map<String, Object> entityMap = objectMapper.convertValue(data, new TypeReference<>() {});
    return createOrUpdateDocument(indexName, docId, entityMap);
  }

  @Override
  public boolean createOrUpdateDocumentFromObject(
      final String indexName, final String docId, final Object data, final String routing)
      throws IOException {
    final Map<String, Object> entityMap = objectMapper.convertValue(data, new TypeReference<>() {});
    return createOrUpdateDocument(indexName, docId, entityMap, routing);
  }

  @Override
  public String createOrUpdateDocumentFromObject(final String indexName, final Object data)
      throws IOException {
    final Map<String, Object> entityMap = objectMapper.convertValue(data, new TypeReference<>() {});
    return createOrUpdateDocument(indexName, entityMap);
  }

  @Override
  public <R> List<R> searchAll(final String index, final Class<R> clazz) throws IOException {
    final var searchRequest =
        new SearchRequest.Builder().index(index).query(ElasticsearchUtil.matchAllQuery()).build();
    final var response = es8Client.search(searchRequest, clazz);
    return response.hits().hits().stream().map(Hit::source).filter(Objects::nonNull).toList();
  }

  @Override
  public <R> List<R> searchTerm(
      final String index,
      final String field,
      final Object value,
      final Class<R> clazz,
      final int size)
      throws IOException {
    final var query = ElasticsearchUtil.termsQuery(field, value);
    final var searchRequest =
        new SearchRequest.Builder().index(index).query(query).size(size).build();
    final var response = es8Client.search(searchRequest, clazz);
    return response.hits().hits().stream().map(Hit::source).filter(Objects::nonNull).toList();
  }

  @Override
  public List<VariableEntity> getVariablesByProcessInstanceKey(
      final String index, final Long processInstanceKey) {
    final var processInstanceKeyQuery =
        ElasticsearchUtil.termsQuery(VariableTemplate.PROCESS_INSTANCE_KEY, processInstanceKey);
    final var query = ElasticsearchUtil.constantScoreQuery(processInstanceKeyQuery);
    final var searchRequestBuilder = new SearchRequest.Builder().index(index).query(query);
    try {
      return ElasticsearchUtil.scrollAllStream(es8Client, searchRequestBuilder, VariableEntity.class)
          .flatMap(res -> res.hits().hits().stream())
          .map(Hit::source)
          .filter(Objects::nonNull)
          .toList();
    } catch (final Exception e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining variables: %s for processInstanceKey %s",
              e.getMessage(), processInstanceKey);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public List<ProcessInstanceForListViewEntity> getProcessInstances(
      final String indexName, final List<Long> ids) throws IOException {
    final var idsQuery = ElasticsearchUtil.idsQuery(toSafeArrayOfStrings(ids));
    final var isProcessInstanceQuery =
        ElasticsearchUtil.termsQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION);
    final var query =
        ElasticsearchUtil.constantScoreQuery(
            ElasticsearchUtil.joinWithAnd(idsQuery, isProcessInstanceQuery));

    final var searchRequest =
        new SearchRequest.Builder().index(indexName).query(query).size(100).build();
    final var response = es8Client.search(searchRequest, ProcessInstanceForListViewEntity.class);
    return response.hits().hits().stream().map(Hit::source).filter(Objects::nonNull).toList();
  }

  private boolean createOrUpdateDocument(
      final String name, final String id, final Map<String, ?> doc, final String routing)
      throws IOException {
    final var requestBuilder =
        new IndexRequest.Builder<Map<String, ?>>().index(name).id(id).document(doc);
    if (routing != null) {
      requestBuilder.routing(routing);
    }
    final IndexResponse response = es8Client.index(requestBuilder.build());
    final Result result = response.result();
    return result == Result.Created || result == Result.Updated;
  }

  private boolean createOrUpdateDocument(
      final String name, final String id, final Map<String, ?> doc) throws IOException {
    return createOrUpdateDocument(name, id, doc, null);
  }

  private String createOrUpdateDocument(final String name, final Map<String, ?> doc)
      throws IOException {
    final String docId = UUID.randomUUID().toString();
    if (createOrUpdateDocument(name, docId, doc)) {
      return docId;
    } else {
      return null;
    }
  }
}
