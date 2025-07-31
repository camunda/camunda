/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util.searchrepository;

import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.and;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.constantScore;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.ids;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.longTerms;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.matchAll;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.script;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.stringTerms;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.getIndexRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.indexRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.reindexRequestBuilder;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.util.CollectionUtil.toSafeArrayOfStrings;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.VariableEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.schema.templates.VariableTemplate;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.store.opensearch.client.sync.ZeebeRichOpenSearchClient;
import io.camunda.operate.store.opensearch.dsl.RequestDSL;
import io.camunda.operate.util.Convertable;
import io.camunda.operate.util.MapPath;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.mapping.DynamicMapping;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.IndexRequest.Builder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpensearchCondition.class)
public class TestOpenSearchRepository implements TestSearchRepository {
  @Autowired private RichOpenSearchClient richOpenSearchClient;

  @Autowired private ZeebeRichOpenSearchClient zeebeRichOpenSearchClient;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private OpenSearchClient openSearchClient;

  @Override
  public boolean isConnected() {
    return richOpenSearchClient != null;
  }

  @Override
  public boolean isZeebeConnected() {
    return zeebeRichOpenSearchClient != null;
  }

  @Override
  public boolean createIndex(final String indexName, final Map<String, ?> mapping)
      throws Exception {
    return true;
  }

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
  public boolean createOrUpdateDocument(
      final String indexName, final String id, final Map<String, ?> doc) {
    return createOrUpdateDocument(indexName, id, doc, null);
  }

  @Override
  public boolean createOrUpdateDocument(
      final String indexName, final String id, final Map<String, ?> doc, final String routing) {
    final Builder<Object> document = indexRequestBuilder(indexName).id(id).document(doc);
    if (routing != null) {
      document.routing(routing);
    }
    return richOpenSearchClient.doc().indexWithRetries(document);
  }

  @Override
  public String createOrUpdateDocument(final String indexName, final Map<String, ?> doc)
      throws IOException {
    final String docId = UUID.randomUUID().toString();
    if (createOrUpdateDocument(indexName, UUID.randomUUID().toString(), doc)) {
      return docId;
    } else {
      return null;
    }
  }

  @Override
  public void deleteById(final String index, final String id) throws IOException {
    richOpenSearchClient.doc().delete(index, id);
  }

  @Override
  public Set<String> getFieldNames(final String indexName) throws IOException {
    final var requestBuilder = getIndexRequestBuilder(indexName);
    return richOpenSearchClient
        .index()
        .get(requestBuilder)
        .get(indexName)
        .mappings()
        .properties()
        .keySet();
  }

  @Override
  public boolean hasDynamicMapping(
      final String indexName, final DynamicMappingType dynamicMappingType) throws IOException {
    final var osDynamicMappingType =
        switch (dynamicMappingType) {
          case Strict -> DynamicMapping.Strict;
          case True -> DynamicMapping.True;
        };

    final var requestBuilder = getIndexRequestBuilder(indexName);
    final var dynamicMapping =
        richOpenSearchClient.index().get(requestBuilder).get(indexName).mappings().dynamic();

    return dynamicMapping == osDynamicMappingType;
  }

  @Override
  public List<String> getAliasNames(final String indexName) throws IOException {
    final var requestBuilder = getIndexRequestBuilder(indexName);
    return richOpenSearchClient
        .index()
        .get(requestBuilder)
        .get(indexName)
        .aliases()
        .keySet()
        .stream()
        .toList();
  }

  @Override
  public <R> List<R> searchAll(final String index, final Class<R> clazz) throws IOException {
    final var requestBuilder = searchRequestBuilder(index).query(matchAll());
    return richOpenSearchClient.doc().searchValues(requestBuilder, clazz);
  }

  @Override
  public <T> List<T> searchJoinRelation(
      final String index, final String joinRelation, final Class<T> clazz, final int size)
      throws IOException {
    final var searchRequestBuilder =
        searchRequestBuilder(index)
            .query(constantScore(term(JOIN_RELATION, joinRelation)))
            .size(size);

    return richOpenSearchClient.doc().searchValues(searchRequestBuilder, clazz);
  }

  @Override
  public <R> List<R> searchTerm(
      final String index,
      final String field,
      final Object value,
      final Class<R> clazz,
      final int size)
      throws IOException {
    Query query = null;

    if (value instanceof final Long l) {
      query = term(field, l);
    }

    if (value instanceof final String s) {
      query = term(field, s);
    }

    if (query == null) {
      throw new UnsupportedOperationException(
          getClass().getName()
              + ".searchTerm is missing implementation for value type "
              + value.getClass().getName());
    }

    final var requestBuilder = searchRequestBuilder(index).query(query).size(size);

    return richOpenSearchClient.doc().searchValues(requestBuilder, clazz);
  }

  @Override
  public <R> List<R> searchTerms(
      final String index,
      final Map<String, Object> fieldValueMap,
      final Class<R> clazz,
      final int size)
      throws IOException {
    final List<Query> queryList = new LinkedList<>();
    fieldValueMap.forEach(
        (field, value) -> {
          Query query = null;

          if (value instanceof final Long l) {
            query = term(field, l);
          }

          if (value instanceof final String s) {
            query = term(field, s);
          }
          queryList.add(query);
        });
    final var queryTerms = queryList.stream().filter(Objects::nonNull).toList();

    if (!queryTerms.isEmpty()) {
      final var requestBuilder = searchRequestBuilder(index).query(and(queryTerms)).size(size);

      return richOpenSearchClient.doc().searchValues(requestBuilder, clazz);
    } else {
      return List.of();
    }
  }

  @Override
  public List<Long> searchIds(
      final String index, final String idFieldName, final List<Long> ids, final int size)
      throws IOException {
    final var searchRequestBuilder =
        searchRequestBuilder(index).query(longTerms(idFieldName, ids)).size(size);

    return richOpenSearchClient.doc().scrollValues(searchRequestBuilder, HashMap.class).stream()
        .map(map -> (Long) map.get(idFieldName))
        .toList();
  }

  @Override
  public void deleteByTermsQuery(
      final String index, final String fieldName, final List<Long> values) throws IOException {
    richOpenSearchClient.doc().deleteByQuery(index, longTerms(fieldName, values));
  }

  @Override
  public void update(final String index, final String id, final Map<String, Object> fields)
      throws IOException {
    final Function<Exception, String> errorMessageSupplier =
        e ->
            String.format(
                "Exception occurred, while executing update request with script for index %s [id=%s]",
                index, id);

    final String script =
        fields.keySet().stream()
            .map(key -> "ctx._source." + key + " = params." + key + ";\n")
            .collect(Collectors.joining());

    final var updateRequestBuilder =
        RequestDSL.<Void, Void>updateRequestBuilder(index).id(id).script(script(script, fields));

    richOpenSearchClient.doc().update(updateRequestBuilder, errorMessageSupplier);
  }

  @Override
  public List<VariableEntity> getVariablesByProcessInstanceKey(
      final String index, final Long processInstanceKey) {
    final var requestBuilder =
        searchRequestBuilder(index)
            .query(constantScore(term(VariableTemplate.PROCESS_INSTANCE_KEY, processInstanceKey)));

    return richOpenSearchClient.doc().scrollValues(requestBuilder, VariableEntity.class);
  }

  @Override
  public void reindex(
      final String srcIndex,
      final String dstIndex,
      final String script,
      final Map<String, Object> scriptParams)
      throws IOException {
    final var request = reindexRequestBuilder(srcIndex, dstIndex, script, scriptParams).build();
    richOpenSearchClient.index().reindexWithRetries(request);
  }

  @Override
  public boolean ilmPolicyExists(final String policyName) {
    return !richOpenSearchClient.ism().getPolicy(policyName).isEmpty();
  }

  @Override
  public IndexSettings getIndexSettings(final String indexName) throws IOException {
    final var settings = new MapPath(richOpenSearchClient.index().getIndexSettings(indexName));
    final String shards =
        settings
            .getByPath("settings", "index", "number_of_shards")
            .flatMap(Convertable::<String>to)
            .orElse(null);
    final String replicas =
        settings
            .getByPath("settings", "index", "number_of_replicas")
            .flatMap(Convertable::<String>to)
            .orElse(null);
    return new IndexSettings(
        shards == null ? null : Integer.parseInt(shards),
        replicas == null ? null : Integer.parseInt(replicas));
  }

  @Override
  public List<BatchOperationEntity> getBatchOperationEntities(
      final String indexName, final List<String> ids) throws IOException {
    final var searchRequestBuilder =
        searchRequestBuilder(indexName)
            .query(constantScore(ids(toSafeArrayOfStrings(ids))))
            .size(100);

    return richOpenSearchClient
        .doc()
        .searchValues(searchRequestBuilder, BatchOperationEntity.class, true);
  }

  @Override
  public List<ProcessInstanceForListViewEntity> getProcessInstances(
      final String indexName, final List<Long> ids) throws IOException {
    final var searchRequestBuilder =
        searchRequestBuilder(indexName)
            .query(
                constantScore(
                    and(
                        ids(toSafeArrayOfStrings(ids)),
                        term(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION))))
            .size(100);

    return richOpenSearchClient
        .doc()
        .searchValues(searchRequestBuilder, ProcessInstanceForListViewEntity.class, true);
  }

  @Override
  public Optional<List<Long>> getIds(
      final String indexName,
      final String idFieldName,
      final List<Long> ids,
      final boolean ignoreAbsentIndex)
      throws IOException {
    try {
      final var searchRequestBuilder =
          searchRequestBuilder(indexName)
              .query(stringTerms(idFieldName, Arrays.asList(toSafeArrayOfStrings(ids))))
              .size(100);

      final List<Long> indexIds =
          richOpenSearchClient.doc().scrollValues(searchRequestBuilder, HashMap.class).stream()
              .map(map -> (Long) map.get(idFieldName))
              .toList();

      return Optional.of(indexIds);
    } catch (final OpenSearchException ex) {
      if (!ex.getMessage().contains("index_not_found_exception") || !ignoreAbsentIndex) {
        throw ex;
      }
      return Optional.empty();
    }
  }

  @Override
  public Long getIndexTemplatePriority(final String templateName) {
    try {
      final var response =
          openSearchClient.indices().getIndexTemplate(req -> req.name(templateName));
      if (response.indexTemplates().isEmpty()) {
        throw new IllegalStateException(templateName + " index template not found");
      }
      return response.indexTemplates().get(0).indexTemplate().priority();
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }
}
