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
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.requestOptions;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.VariableEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.templates.VariableTemplate;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.ElasticsearchUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indexlifecycle.GetLifecyclePolicyRequest;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetComposableIndexTemplateRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.IdsQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticsearchCondition.class)
public class TestElasticSearchRepository implements TestSearchRepository {
  @Autowired private RestHighLevelClient esClient;

  @Autowired
  @Qualifier("zeebeEsClient")
  private RestHighLevelClient zeebeEsClient;

  @Autowired
  @Qualifier("operateObjectMapper")
  private ObjectMapper objectMapper;

  @Override
  public boolean isConnected() {
    return esClient != null;
  }

  @Override
  public boolean isZeebeConnected() {
    return zeebeEsClient != null;
  }

  @Override
  public boolean createIndex(final String indexName, final Map<String, ?> mapping)
      throws IOException {
    return esClient
        .indices()
        .create(new CreateIndexRequest(indexName).mapping(mapping), RequestOptions.DEFAULT)
        .isAcknowledged();
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
      final String name, final String id, final Map<String, ?> doc) throws IOException {
    return createOrUpdateDocument(name, id, doc, null);
  }

  @Override
  public boolean createOrUpdateDocument(
      final String name, final String id, final Map<String, ?> doc, final String routing)
      throws IOException {
    final IndexRequest source = new IndexRequest(name).id(id).source(doc, XContentType.JSON);
    if (routing != null) {
      source.routing(routing);
    }
    final IndexResponse response = esClient.index(source, RequestOptions.DEFAULT);
    final DocWriteResponse.Result result = response.getResult();
    return result.equals(DocWriteResponse.Result.CREATED)
        || result.equals(DocWriteResponse.Result.UPDATED);
  }

  @Override
  public String createOrUpdateDocument(final String name, final Map<String, ?> doc)
      throws IOException {
    final String docId = UUID.randomUUID().toString();
    if (createOrUpdateDocument(name, UUID.randomUUID().toString(), doc)) {
      return docId;
    } else {
      return null;
    }
  }

  @Override
  public void deleteById(final String index, final String id) throws IOException {
    final DeleteRequest request = new DeleteRequest().index(index).id(id);
    esClient.delete(request, RequestOptions.DEFAULT);
  }

  @Override
  public Set<String> getFieldNames(final String indexName) throws IOException {
    return ((Map<String, Object>) getMappingSource(indexName).get("properties")).keySet();
  }

  @Override
  public boolean hasDynamicMapping(
      final String indexName, final DynamicMappingType dynamicMappingType) throws IOException {
    final var esDynamicMappingType =
        switch (dynamicMappingType) {
          case Strict -> "strict";
          case True -> "true";
        };

    return getMappingSource(indexName).get("dynamic").equals(esDynamicMappingType);
  }

  @Override
  public List<String> getAliasNames(final String indexName) throws IOException {
    return esClient
        .indices()
        .get(new GetIndexRequest(indexName), RequestOptions.DEFAULT)
        .getAliases()
        .get(indexName)
        .stream()
        .map(aliasMetadata -> aliasMetadata.alias())
        .toList();
  }

  @Override
  public <R> List<R> searchAll(final String index, final Class<R> clazz) throws IOException {
    final SearchRequest searchRequest =
        new SearchRequest(index).source(new SearchSourceBuilder().query(matchAllQuery()));
    final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
    return ElasticsearchUtil.mapSearchHits(response.getHits().getHits(), objectMapper, clazz);
  }

  @Override
  public <T> List<T> searchJoinRelation(
      final String index, final String joinRelation, final Class<T> clazz, final int size)
      throws IOException {
    final TermQueryBuilder isProcessInstanceQuery = termQuery(JOIN_RELATION, joinRelation);

    final SearchRequest searchRequest =
        new SearchRequest(index)
            .source(
                new SearchSourceBuilder()
                    .query(constantScoreQuery(isProcessInstanceQuery))
                    .size(size));

    final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

    return ElasticsearchUtil.mapSearchHits(response.getHits().getHits(), objectMapper, clazz);
  }

  @Override
  public <R> List<R> searchTerm(
      final String index,
      final String field,
      final Object value,
      final Class<R> clazz,
      final int size)
      throws IOException {
    final var request =
        new SearchRequest(index)
            .source(new SearchSourceBuilder().query(QueryBuilders.termQuery(field, value)));

    final var response = esClient.search(request, RequestOptions.DEFAULT);

    return ElasticsearchUtil.mapSearchHits(response.getHits().getHits(), objectMapper, clazz);
  }

  @Override
  public <R> List<R> searchTerms(
      final String index,
      final Map<String, Object> fieldValueMap,
      final Class<R> clazz,
      final int size)
      throws IOException {
    final List<QueryBuilder> queryBuilders = new ArrayList<>();
    fieldValueMap.forEach(
        (field, value) -> {
          if (value != null) {
            queryBuilders.add(termQuery(field, value));
          }
        });

    final var request =
        new SearchRequest(index)
            .source(
                new SearchSourceBuilder()
                    .query(joinWithAnd(queryBuilders.toArray(new QueryBuilder[] {}))));
    final var response = esClient.search(request, RequestOptions.DEFAULT);

    return ElasticsearchUtil.mapSearchHits(response.getHits().getHits(), objectMapper, clazz);
  }

  @Override
  public List<Long> searchIds(
      final String index, final String idFieldName, final List<Long> ids, final int size)
      throws IOException {
    final TermsQueryBuilder q =
        QueryBuilders.termsQuery(idFieldName, CollectionUtil.toSafeArrayOfStrings(ids));
    final SearchRequest request =
        new SearchRequest(index).source(new SearchSourceBuilder().query(q).size(size));
    return ElasticsearchUtil.scrollFieldToList(request, idFieldName, esClient);
  }

  @Override
  public void deleteByTermsQuery(
      final String index, final String fieldName, final List<Long> values) throws IOException {
    final DeleteByQueryRequest request =
        new DeleteByQueryRequest(index).setQuery(termsQuery(fieldName, values));
    esClient.deleteByQuery(request, RequestOptions.DEFAULT);
  }

  @Override
  public void update(final String index, final String id, final Map<String, Object> fields)
      throws IOException {
    final UpdateRequest request = new UpdateRequest().index(index).id(id).doc(fields);
    esClient.update(request, RequestOptions.DEFAULT);
  }

  @Override
  public List<VariableEntity> getVariablesByProcessInstanceKey(
      final String index, final Long processInstanceKey) {
    final TermQueryBuilder processInstanceKeyQuery =
        termQuery(VariableTemplate.PROCESS_INSTANCE_KEY, processInstanceKey);
    final ConstantScoreQueryBuilder query = constantScoreQuery(processInstanceKeyQuery);
    final SearchRequest searchRequest =
        new SearchRequest(index).source(new SearchSourceBuilder().query(query));
    try {
      return ElasticsearchUtil.scroll(searchRequest, VariableEntity.class, objectMapper, esClient);
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining variables: %s for processInstanceKey %s",
              e.getMessage(), processInstanceKey);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public void reindex(
      final String srcIndex,
      final String dstIndex,
      final String script,
      final Map<String, Object> scriptParams)
      throws IOException {
    final ReindexRequest reindexRequest =
        new ReindexRequest()
            .setSourceIndices(srcIndex)
            .setDestIndex(dstIndex)
            .setScript(
                new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, script, scriptParams));

    esClient.reindex(reindexRequest, RequestOptions.DEFAULT);
  }

  @Override
  public boolean ilmPolicyExists(final String policyName) throws IOException {
    final var request = new GetLifecyclePolicyRequest(policyName);
    return esClient
            .indexLifecycle()
            .getLifecyclePolicy(request, requestOptions)
            .getPolicies()
            .get(policyName)
        != null;
  }

  @Override
  public IndexSettings getIndexSettings(final String indexName) throws IOException {
    final var settings =
        esClient
            .indices()
            .get(new GetIndexRequest(indexName), RequestOptions.DEFAULT)
            .getSettings()
            .get(indexName);
    return new IndexSettings(
        settings.getAsInt("index.number_of_shards", null),
        settings.getAsInt("index.number_of_replicas", null));
  }

  @Override
  public List<BatchOperationEntity> getBatchOperationEntities(
      final String indexName, final List<String> ids) throws IOException {
    final IdsQueryBuilder idsQ = idsQuery().addIds(CollectionUtil.toSafeArrayOfStrings(ids));

    final SearchRequest searchRequest =
        new SearchRequest(indexName)
            .source(new SearchSourceBuilder().query(constantScoreQuery(idsQ)).size(100));

    final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

    return ElasticsearchUtil.mapSearchHits(
        response.getHits().getHits(), objectMapper, BatchOperationEntity.class);
  }

  @Override
  public List<ProcessInstanceForListViewEntity> getProcessInstances(
      final String indexName, final List<Long> ids) throws IOException {
    final IdsQueryBuilder idsQ = idsQuery().addIds(CollectionUtil.toSafeArrayOfStrings(ids));
    final TermQueryBuilder isProcessInstanceQuery =
        termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION);

    final SearchRequest searchRequest =
        new SearchRequest(indexName)
            .source(
                new SearchSourceBuilder()
                    .query(constantScoreQuery(joinWithAnd(idsQ, isProcessInstanceQuery)))
                    .size(100));

    final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

    return ElasticsearchUtil.mapSearchHits(
        response.getHits().getHits(), objectMapper, ProcessInstanceForListViewEntity.class);
  }

  @Override
  public Optional<List<Long>> getIds(
      final String indexName,
      final String idFieldName,
      final List<Long> ids,
      final boolean ignoreAbsentIndex)
      throws IOException {
    try {
      final TermsQueryBuilder q = termsQuery(idFieldName, CollectionUtil.toSafeArrayOfStrings(ids));
      final SearchRequest request =
          new SearchRequest(indexName).source(new SearchSourceBuilder().query(q).size(100));
      return Optional.of(ElasticsearchUtil.scrollFieldToList(request, idFieldName, esClient));
    } catch (final ElasticsearchStatusException ex) {
      if (!ex.getMessage().contains("index_not_found_exception") || !ignoreAbsentIndex) {
        throw ex;
      }
      return Optional.empty();
    }
  }

  @Override
  public Long getIndexTemplatePriority(final String templateName) {
    try {
      final var request = new GetComposableIndexTemplateRequest(templateName);
      final var response = esClient.indices().getIndexTemplate(request, RequestOptions.DEFAULT);
      final var templates = response.getIndexTemplates();
      if (templates.isEmpty()) {
        throw new IllegalStateException(templateName + " index template not found");
      }
      return templates.get(templateName).priority();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private Map<String, Object> getMappingSource(final String indexName) throws IOException {
    return esClient
        .indices()
        .get(new GetIndexRequest(indexName), RequestOptions.DEFAULT)
        .getMappings()
        .get(indexName)
        .getSourceAsMap();
  }
}
