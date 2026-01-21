/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.elasticsearch.dao;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ExpandWildcard;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.store.elasticsearch.dao.response.AggregationResponse;
import io.camunda.operate.store.elasticsearch.dao.response.InsertResponse;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.entities.ExporterEntity;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenericDAO<T extends ExporterEntity, I extends IndexDescriptor> {

  private static final Logger LOGGER = LoggerFactory.getLogger(GenericDAO.class);
  private ElasticsearchClient esClient;
  private ObjectMapper objectMapper;
  private I index;

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
  GenericDAO(final ObjectMapper objectMapper, final I index, final ElasticsearchClient esClient) {
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
  }

  /**
   * Returns the Elasticsearch IndexRequest. This is for a very specific use case. We'd rather leave
   * this DAO class clean and not return any elastic class
   *
   * @param entity
   * @return insert request
   */
  public IndexRequest<T> buildESIndexRequest(final T entity) {
    return IndexRequest.of(
        i -> i.index(index.getFullQualifiedName()).id(entity.getId()).document(entity));
  }

  public InsertResponse insert(final T entity) {
    try {
      final var request = buildESIndexRequest(entity);
      final IndexResponse response = esClient.index(request);
      if (response.result() != Result.Created) {
        return InsertResponse.failure();
      }

      return InsertResponse.success();
    } catch (final IOException e) {
      LOGGER.error(e.getMessage(), e);
    }

    throw new OperateRuntimeException("Error while trying to upsert entity: " + entity);
  }

  public AggregationResponse searchWithAggregation(final Query query) {
    try {
      final SearchRequest searchRequest =
          SearchRequest.of(
              s ->
                  s.index(index.getFullQualifiedName())
                      .allowNoIndices(true)
                      .ignoreUnavailable(true)
                      .expandWildcards(ExpandWildcard.Open)
                      .query(query.getEsQuery())
                      .aggregations(query.getGroupName(), query.getAggregation()));

      final SearchResponse<Void> response = esClient.search(searchRequest, Void.class);

      if (response.aggregations() == null) {
        throw new OperateRuntimeException("Search with aggregation returned no aggregation");
      }

      final var aggregation = response.aggregations().get(query.getGroupName());
      if (aggregation == null || aggregation.sum() == null) {
        throw new OperateRuntimeException("Unexpected response for aggregations");
      }

      return new AggregationResponse(false, null, (long) aggregation.sum().value());
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
    private ElasticsearchClient esClient;
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

    public Builder<T, I> esClient(final ElasticsearchClient esClient) {
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
