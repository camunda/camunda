/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util.searchrepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.VariableEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.templates.VariableTemplate;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.ElasticsearchUtil;
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
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.IdsQueryBuilder;
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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.requestOptions;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Component
@Conditional(ElasticsearchCondition.class)
public class TestElasticSearchRepository implements TestSearchRepository {
  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  @Qualifier("zeebeEsClient")
  private RestHighLevelClient zeebeEsClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Override
  public <R> List<R> searchAll(String index, Class<R> clazz) throws IOException {
    final SearchRequest searchRequest = new SearchRequest(index)
      .source(new SearchSourceBuilder()
        .query(matchAllQuery()));
    final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
    return ElasticsearchUtil.mapSearchHits(response.getHits().getHits(), objectMapper, clazz);
  }

  @Override
  public boolean isConnected() {
    return esClient != null;
  }

  @Override
  public boolean isZeebeConnected() {
    return zeebeEsClient != null;
  }

  @Override
  public boolean createIndex(String indexName, Map<String, ?> mapping) throws IOException {
    return esClient.indices().create(new CreateIndexRequest(indexName).mapping(mapping), RequestOptions.DEFAULT)
        .isAcknowledged();
  }

  @Override
  public boolean createOrUpdateDocument(String name, String id, Map<String, String> doc) throws IOException {
    final IndexResponse response = esClient.index(new IndexRequest(name).id(id)
            .source(doc, XContentType.JSON), RequestOptions.DEFAULT);
    DocWriteResponse.Result result = response.getResult();
    return result.equals(DocWriteResponse.Result.CREATED) || result.equals(DocWriteResponse.Result.UPDATED);
  }

  @Override
  public Set<String> getFieldNames(String indexName) throws IOException {
    return ((Map<String, Object>) getMappingSource(indexName).get("properties")).keySet();
  }

  private Map<String, Object> getMappingSource(String indexName) throws IOException {
    return esClient.indices()
      .get(new GetIndexRequest(indexName), RequestOptions.DEFAULT)
      .getMappings()
      .get(indexName)
      .getSourceAsMap();
  }

  @Override
  public boolean hasDynamicMapping(String indexName, DynamicMappingType dynamicMappingType) throws IOException {
    var esDynamicMappingType = switch(dynamicMappingType) {
      case Strict -> "strict";
      case True -> "true";
    };

    return getMappingSource(indexName)
      .get("dynamic")
      .equals(esDynamicMappingType);
  }

  @Override
  public List<String> getAliasNames(String indexName) throws IOException {
    return esClient.indices()
      .get(new GetIndexRequest(indexName), RequestOptions.DEFAULT)
      .getAliases()
      .get(indexName)
      .stream()
      .map(aliasMetadata -> aliasMetadata.alias())
      .toList();
  }

  @Override
  public <T> List<T> searchJoinRelation(String index, String joinRelation, Class<T> clazz, int size) throws IOException {
    final TermQueryBuilder isProcessInstanceQuery = termQuery(JOIN_RELATION, joinRelation);

    final SearchRequest searchRequest = new SearchRequest(index)
      .source(new SearchSourceBuilder()
        .query(constantScoreQuery(isProcessInstanceQuery))
        .size(size));

    final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

    return ElasticsearchUtil.mapSearchHits(response.getHits().getHits(), objectMapper, clazz);
  }

  @Override
  public List<Long> searchIds(String index, String idFieldName, List<Long> ids, int size) throws IOException {
    final TermsQueryBuilder q = QueryBuilders.termsQuery(idFieldName, CollectionUtil.toSafeArrayOfStrings(ids));
    final SearchRequest request = new SearchRequest(index)
      .source(new SearchSourceBuilder()
        .query(q)
        .size(size));
    return ElasticsearchUtil.scrollFieldToList(request, idFieldName, esClient);
  }

  @Override
  public void deleteByTermsQuery(String index, String fieldName, List<Long> values) throws IOException {
    DeleteByQueryRequest request =
        new DeleteByQueryRequest(index).setQuery(termsQuery(fieldName, values));
    esClient.deleteByQuery(request, RequestOptions.DEFAULT);
  }

  @Override
  public <A, R> List<R> searchTerm(String index, String field, A value, Class<R> clazz, int size) throws IOException {
    var request = new SearchRequest(index).source(
        new SearchSourceBuilder().query(QueryBuilders.termQuery(field, value))
      );

    var response = esClient.search(request, RequestOptions.DEFAULT);

    return ElasticsearchUtil.mapSearchHits(response.getHits().getHits(), objectMapper, clazz);
  }

  @Override
  public void deleteById(String index, String id) throws IOException {
    DeleteRequest request = new DeleteRequest().index(index).id(id);
    esClient.delete(request, RequestOptions.DEFAULT);
  }

  @Override
  public void update(String index, String id, Map<String, Object> fields) throws IOException {
    UpdateRequest request = new UpdateRequest().index(index).id(id).doc(fields);
    esClient.update(request, RequestOptions.DEFAULT);
  }

  @Override
  public List<VariableEntity> getVariablesByProcessInstanceKey(String index, Long processInstanceKey) {
    final TermQueryBuilder processInstanceKeyQuery = termQuery(VariableTemplate.PROCESS_INSTANCE_KEY, processInstanceKey);
    final ConstantScoreQueryBuilder query = constantScoreQuery(processInstanceKeyQuery);
    final SearchRequest searchRequest = new SearchRequest(index).source(new SearchSourceBuilder().query(query));
    try {
      return ElasticsearchUtil.scroll(searchRequest, VariableEntity.class, objectMapper, esClient);
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining variables: %s for processInstanceKey %s",
        e.getMessage(), processInstanceKey);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public void reindex(String srcIndex, String dstIndex, String script, Map<String, Object> scriptParams) throws IOException {
    ReindexRequest reindexRequest = new ReindexRequest().setSourceIndices(srcIndex)
      .setDestIndex(dstIndex)
      .setScript(new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, script, scriptParams));

    esClient.reindex(reindexRequest, RequestOptions.DEFAULT);
  }

  @Override
  public boolean ilmPolicyExists(String policyName) throws IOException {
    var request = new GetLifecyclePolicyRequest(policyName);
    return esClient.indexLifecycle().getLifecyclePolicy(request, requestOptions).getPolicies().get(policyName) != null;
  }

  @Override
  public IndexSettings getIndexSettings(String indexName) throws IOException {
    var settings = esClient.indices()
      .get(new GetIndexRequest(indexName), RequestOptions.DEFAULT)
      .getSettings()
      .get(indexName);
    return new IndexSettings(
      settings.getAsInt("index.number_of_shards", null),
      settings.getAsInt("index.number_of_replicas", null)
    );
  }

  @Override
  public List<BatchOperationEntity> getBatchOperationEntities(String indexName, List<String> ids) throws IOException {
    final IdsQueryBuilder idsQ = idsQuery().addIds(CollectionUtil.toSafeArrayOfStrings(ids));

    final SearchRequest searchRequest = new SearchRequest(indexName)
      .source(new SearchSourceBuilder()
        .query(constantScoreQuery(idsQ))
        .size(100));

    final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

    return ElasticsearchUtil.mapSearchHits(response.getHits().getHits(), objectMapper, BatchOperationEntity.class);
  }

  @Override
  public List<ProcessInstanceForListViewEntity> getProcessInstances(String indexName, List<Long> ids) throws IOException {
    final IdsQueryBuilder idsQ = idsQuery().addIds(CollectionUtil.toSafeArrayOfStrings(ids));
    final TermQueryBuilder isProcessInstanceQuery = termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION);

    final SearchRequest searchRequest = new SearchRequest(indexName)
      .source(new SearchSourceBuilder()
        .query(constantScoreQuery(joinWithAnd(idsQ, isProcessInstanceQuery)))
        .size(100));

    final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

    return ElasticsearchUtil.mapSearchHits(response.getHits().getHits(), objectMapper, ProcessInstanceForListViewEntity.class);
  }

  @Override
  public Optional<List<Long>> getIds(String indexName, String idFieldName, List<Long> ids, boolean ignoreAbsentIndex) throws IOException {
    try {
      final TermsQueryBuilder q = termsQuery(idFieldName, CollectionUtil.toSafeArrayOfStrings(ids));
      final SearchRequest request = new SearchRequest(indexName)
        .source(new SearchSourceBuilder()
          .query(q)
          .size(100));
      return Optional.of(ElasticsearchUtil.scrollFieldToList(request, idFieldName, esClient));
    } catch (ElasticsearchStatusException ex) {
      if (!ex.getMessage().contains("index_not_found_exception") || !ignoreAbsentIndex) {
        throw ex;
      }
      return Optional.empty();
    }
  }
}
