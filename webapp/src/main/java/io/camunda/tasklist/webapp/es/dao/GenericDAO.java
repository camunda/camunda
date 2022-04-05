/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.es.dao;

import static io.camunda.tasklist.webapp.es.dao.response.AggregationResponse.AggregationValue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.TasklistEntity;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.indices.IndexDescriptor;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.tasklist.webapp.es.dao.response.AggregationResponse;
import io.camunda.tasklist.webapp.es.dao.response.InsertResponse;
import io.camunda.tasklist.webapp.es.dao.response.SearchResponse;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.stream.Collectors;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenericDAO<T extends TasklistEntity, I extends IndexDescriptor> {

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

  public InsertResponse insert(T entity) {
    try {

      final IndexRequest request =
          new IndexRequest(index.getFullQualifiedName())
              .id(entity.getId())
              .source(objectMapper.writeValueAsString(entity), XContentType.JSON);
      final IndexResponse response = esClient.index(request, RequestOptions.DEFAULT);
      if (response.status() != RestStatus.CREATED) {
        return InsertResponse.failure();
      }

      return InsertResponse.success();
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
    }

    throw new TasklistRuntimeException("Error while trying to upsert entity: " + entity);
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

  public AggregationResponse searchWithAggregation(Query query) {
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
        throw new TasklistRuntimeException("Search with aggregation returned no aggregation");
      }

      final Aggregation group = aggregations.get(query.getGroupName());
      if (!(group instanceof ParsedStringTerms)) {
        throw new TasklistRuntimeException("Unexpected response for aggregations");
      }

      final ParsedStringTerms terms = (ParsedStringTerms) group;
      final List<ParsedStringTerms.ParsedBucket> buckets =
          (List<ParsedStringTerms.ParsedBucket>) terms.getBuckets();

      final List<AggregationValue> values =
          buckets.stream()
              .map(it -> new AggregationValue(String.valueOf(it.getKey()), it.getDocCount()))
              .collect(Collectors.toList());

      return new AggregationResponse(false, values);
    } catch (IOException e) {
      LOGGER.error("Error searching at index: " + index, e);
    }
    return new AggregationResponse(true);
  }

  /**
   * Mostly used for test purposes
   *
   * @param <T> TasklistEntity - Elastic Search doc
   * @param <I> IndexDescriptor - which index to persist the doc
   */
  static class Builder<T extends TasklistEntity, I extends IndexDescriptor> {
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
