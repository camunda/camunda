/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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

  @Override
  public boolean isConnected() {
    return richOpenSearchClient != null;
  }

  @Override
  public boolean isZeebeConnected() {
    return zeebeRichOpenSearchClient != null;
  }

  @Override
  public boolean createIndex(String indexName, Map<String, ?> mapping) throws Exception {
    return true;
  }

  @Override
  public boolean createOrUpdateDocumentFromObject(String indexName, String docId, Object data)
      throws IOException {
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
  public String createOrUpdateDocumentFromObject(String indexName, Object data) throws IOException {
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
  public String createOrUpdateDocument(String indexName, Map<String, ?> doc) throws IOException {
    final String docId = UUID.randomUUID().toString();
    if (createOrUpdateDocument(indexName, UUID.randomUUID().toString(), doc)) {
      return docId;
    } else {
      return null;
    }
  }

  @Override
  public void deleteById(String index, String id) throws IOException {
    richOpenSearchClient.doc().delete(index, id);
  }

  @Override
  public Set<String> getFieldNames(String indexName) throws IOException {
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
  public boolean hasDynamicMapping(String indexName, DynamicMappingType dynamicMappingType)
      throws IOException {
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
  public List<String> getAliasNames(String indexName) throws IOException {
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
  public <R> List<R> searchAll(String index, Class<R> clazz) throws IOException {
    final var requestBuilder = searchRequestBuilder(index).query(matchAll());
    return richOpenSearchClient.doc().searchValues(requestBuilder, clazz);
  }

  @Override
  public <T> List<T> searchJoinRelation(String index, String joinRelation, Class<T> clazz, int size)
      throws IOException {
    final var searchRequestBuilder =
        searchRequestBuilder(index)
            .query(constantScore(term(JOIN_RELATION, joinRelation)))
            .size(size);

    return richOpenSearchClient.doc().searchValues(searchRequestBuilder, clazz);
  }

  @Override
  public <A, R> List<R> searchTerm(String index, String field, A value, Class<R> clazz, int size)
      throws IOException {
    Query query = null;

    if (value instanceof Long l) {
      query = term(field, l);
    }

    if (value instanceof String s) {
      query = term(field, s);
    }

    if (query == null) {
      throw new UnsupportedOperationException(
          this.getClass().getName()
              + ".searchTerm is missing implementation for value type "
              + value.getClass().getName());
    }

    final var requestBuilder = searchRequestBuilder(index).query(query).size(size);

    return richOpenSearchClient.doc().searchValues(requestBuilder, clazz);
  }

  @Override
  public List<Long> searchIds(String index, String idFieldName, List<Long> ids, int size)
      throws IOException {
    final var searchRequestBuilder =
        searchRequestBuilder(index).query(longTerms(idFieldName, ids)).size(size);

    return richOpenSearchClient.doc().scrollValues(searchRequestBuilder, HashMap.class).stream()
        .map(map -> (Long) map.get(idFieldName))
        .toList();
  }

  @Override
  public void deleteByTermsQuery(String index, String fieldName, List<Long> values)
      throws IOException {
    richOpenSearchClient.doc().deleteByQuery(index, longTerms(fieldName, values));
  }

  @Override
  public void update(String index, String id, Map<String, Object> fields) throws IOException {
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
      String index, Long processInstanceKey) {
    final var requestBuilder =
        searchRequestBuilder(index)
            .query(constantScore(term(VariableTemplate.PROCESS_INSTANCE_KEY, processInstanceKey)));

    return richOpenSearchClient.doc().scrollValues(requestBuilder, VariableEntity.class);
  }

  @Override
  public void reindex(
      String srcIndex, String dstIndex, String script, Map<String, Object> scriptParams)
      throws IOException {
    final var request = reindexRequestBuilder(srcIndex, dstIndex, script, scriptParams).build();
    richOpenSearchClient.index().reindexWithRetries(request);
  }

  @Override
  public boolean ilmPolicyExists(String policyName) {
    return !richOpenSearchClient.ism().getPolicy(policyName).isEmpty();
  }

  @Override
  public IndexSettings getIndexSettings(String indexName) throws IOException {
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
  public List<BatchOperationEntity> getBatchOperationEntities(String indexName, List<String> ids)
      throws IOException {
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
      String indexName, List<Long> ids) throws IOException {
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
      String indexName, String idFieldName, List<Long> ids, boolean ignoreAbsentIndex)
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
    } catch (OpenSearchException ex) {
      if (!ex.getMessage().contains("index_not_found_exception") || !ignoreAbsentIndex) {
        throw ex;
      }
      return Optional.empty();
    }
  }
}
