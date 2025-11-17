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
package io.camunda.operate.store.elasticsearch.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.store.AggregationResult;
import io.camunda.operate.store.elasticsearch.dao.response.InsertResponse;
import io.camunda.operate.store.elasticsearch.dao.response.SearchResponse;
import io.camunda.operate.util.ElasticsearchUtil;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.ParsedCardinality;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenericDAO<T extends OperateEntity, I extends IndexDescriptor> {

  private static final Logger LOGGER = LoggerFactory.getLogger(GenericDAO.class);
  private RestHighLevelClient esClient;
  private ObjectMapper objectMapper;
  private I index;
  private Class<T> typeOfEntity;

  private GenericDAO() {
    // No constructor, only Builder class should be used
  }

  /**
   * Constructor package accessible for implementations to access
   *
   * @param objectMapper
   * @param index
   * @param esClient
   */
  @SuppressWarnings("unchecked")
  GenericDAO(ObjectMapper objectMapper, I index, RestHighLevelClient esClient) {
    if (objectMapper == null) {
      throw new IllegalStateException("ObjectMapper can't be null");
    }
    if (index == null) {
      throw new IllegalStateException("Index can't be null");
    }
    if (esClient == null) {
      throw new IllegalStateException("ES Client can't be null");
    }

    this.objectMapper = objectMapper;
    this.index = index;
    this.esClient = esClient;
    this.typeOfEntity =
        (Class<T>)
            ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
  }

  /**
   * Returns the Elasticsearch IndexRequest. This is for a very specific use case. We'd rather leave
   * this DAO class clean and not return any elastic class
   *
   * @param entity
   * @return insert request
   */
  public IndexRequest buildESIndexRequest(T entity) {
    try {
      return new IndexRequest(index.getFullQualifiedName())
          .id(entity.getId())
          .source(objectMapper.writeValueAsString(entity), XContentType.JSON);
    } catch (JsonProcessingException e) {
      throw new OperateRuntimeException("error building Index/InserRequest");
    }
  }

  public InsertResponse insert(T entity) {
    try {
      final IndexRequest request = buildESIndexRequest(entity);
      final IndexResponse response = esClient.index(request, RequestOptions.DEFAULT);
      if (response.status() != RestStatus.CREATED) {
        return InsertResponse.failure();
      }

      return InsertResponse.success();
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
    }

    throw new OperateRuntimeException("Error while trying to upsert entity: " + entity);
  }

  public SearchResponse<T> search(Query query) {
    final SearchSourceBuilder source =
        SearchSourceBuilder.searchSource()
            .query(query.getQueryBuilder())
            .aggregation(query.getAggregationBuilder());
    final SearchRequest searchRequest =
        new SearchRequest(index.getFullQualifiedName())
            .indicesOptions(IndicesOptions.lenientExpandOpen())
            .source(source);
    try {
      final List<T> hits =
          ElasticsearchUtil.scroll(searchRequest, typeOfEntity, objectMapper, esClient);
      return new SearchResponse<>(false, hits);
    } catch (IOException e) {
      LOGGER.error("Error searching at index: " + index, e);
    }
    return new SearchResponse<>(true);
  }

  public AggregationResult searchWithAggregation(final Query query) {
    final SearchSourceBuilder source =
        SearchSourceBuilder.searchSource()
            .query(query.getQueryBuilder())
            .aggregation(query.getAggregationBuilder());
    final SearchRequest searchRequest =
        new SearchRequest(index.getFullQualifiedName())
            .indicesOptions(IndicesOptions.lenientExpandOpen())
            .source(source);
    try {

      final Aggregations aggregations =
          esClient.search(searchRequest, RequestOptions.DEFAULT).getAggregations();
      if (aggregations == null) {
        throw new OperateRuntimeException("Search with aggregation returned no aggregation");
      }

      final Aggregation group = aggregations.get(query.getGroupName());
      if (!(group instanceof final ParsedCardinality cardinality)) {
        throw new OperateRuntimeException("Unexpected response for aggregations");
      }

      return new AggregationResult(false, cardinality.getValue());
    } catch (IOException e) {
      LOGGER.error("Error searching at index: " + index, e);
    }
    return AggregationResult.ERROR;
  }

  /**
   * Mostly used for test purposes
   *
   * @param <T> TasklistEntity - Elastic Search doc
   * @param <I> IndexDescriptor - which index to persist the doc
   */
  public static class Builder<T extends OperateEntity, I extends IndexDescriptor> {
    private ObjectMapper objectMapper;
    private RestHighLevelClient esClient;
    private I index;

    public Builder() {}

    public Builder<T, I> objectMapper(ObjectMapper objectMapper) {
      this.objectMapper = objectMapper;
      return this;
    }

    public Builder<T, I> index(I index) {
      this.index = index;
      return this;
    }

    public Builder<T, I> esClient(RestHighLevelClient esClient) {
      this.esClient = esClient;
      return this;
    }

    public GenericDAO<T, I> build() {
      if (objectMapper == null) {
        throw new IllegalStateException("ObjectMapper can't be null");
      }
      if (index == null) {
        throw new IllegalStateException("Index can't be null");
      }
      if (esClient == null) {
        throw new IllegalStateException("ES Client can't be null");
      }

      return new GenericDAO<>(objectMapper, index, esClient);
    }
  }
}
