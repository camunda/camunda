/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.elasticsearch.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.store.elasticsearch.dao.response.AggregationResponse;
import io.camunda.operate.store.elasticsearch.dao.response.InsertResponse;
import io.camunda.operate.store.elasticsearch.dao.response.SearchResponse;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.entities.ExporterEntity;
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

public class GenericDAO<T extends ExporterEntity, I extends IndexDescriptor> {

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
  GenericDAO(final ObjectMapper objectMapper, final I index, final RestHighLevelClient esClient) {
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
    typeOfEntity =
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
  public IndexRequest buildESIndexRequest(final T entity) {
    try {
      return new IndexRequest(index.getFullQualifiedName())
          .id(entity.getId())
          .source(objectMapper.writeValueAsString(entity), XContentType.JSON);
    } catch (final JsonProcessingException e) {
      throw new OperateRuntimeException("error building Index/InserRequest");
    }
  }

  public InsertResponse insert(final T entity) {
    try {
      final IndexRequest request = buildESIndexRequest(entity);
      final IndexResponse response = esClient.index(request, RequestOptions.DEFAULT);
      if (response.status() != RestStatus.CREATED) {
        return InsertResponse.failure();
      }

      return InsertResponse.success();
    } catch (final IOException e) {
      LOGGER.error(e.getMessage(), e);
    }

    throw new OperateRuntimeException("Error while trying to upsert entity: " + entity);
  }

  public SearchResponse<T> search(final Query query) {
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
    } catch (final IOException e) {
      LOGGER.error("Error searching at index: " + index, e);
    }
    return new SearchResponse<>(true);
  }

  public AggregationResponse searchWithAggregation(final Query query) {
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
      if (!(group instanceof ParsedStringTerms)) {
        throw new OperateRuntimeException("Unexpected response for aggregations");
      }

      final ParsedStringTerms terms = (ParsedStringTerms) group;
      final List<ParsedStringTerms.ParsedBucket> buckets =
          (List<ParsedStringTerms.ParsedBucket>) terms.getBuckets();

      final List<AggregationResponse.AggregationValue> values =
          buckets.stream()
              .map(
                  it ->
                      new AggregationResponse.AggregationValue(
                          String.valueOf(it.getKey()), it.getDocCount()))
              .collect(Collectors.toList());

      final long sumOfOtherDocCounts =
          ((ParsedStringTerms) group).getSumOfOtherDocCounts(); // size of documents not in result
      final long total = sumOfOtherDocCounts + values.size(); // size of result + other docs
      return new AggregationResponse(false, values, total);
    } catch (final IOException e) {
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
  public static class Builder<T extends ExporterEntity, I extends IndexDescriptor> {
    private ObjectMapper objectMapper;
    private RestHighLevelClient esClient;
    private I index;

    public Builder() {}

    public Builder<T, I> objectMapper(final ObjectMapper objectMapper) {
      this.objectMapper = objectMapper;
      return this;
    }

    public Builder<T, I> index(final I index) {
      this.index = index;
      return this;
    }

    public Builder<T, I> esClient(final RestHighLevelClient esClient) {
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
