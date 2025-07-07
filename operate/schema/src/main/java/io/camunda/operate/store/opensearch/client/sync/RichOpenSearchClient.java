/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch.client.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.store.opensearch.client.async.OpenSearchAsyncSnapshotOperations;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class RichOpenSearchClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(RichOpenSearchClient.class);
  final Async async;
  final OpenSearchBatchOperations openSearchBatchOperations;
  final OpenSearchClusterOperations openSearchClusterOperations;
  final OpenSearchDocumentOperations openSearchDocumentOperations;
  final OpenSearchIndexOperations openSearchIndexOperations;
  final OpenSearchPipelineOperations openSearchPipelineOperations;
  final OpenSearchSnapshotOperations openSearchSnapshotOperations;
  final OpenSearchTaskOperations openSearchTaskOperations;
  final OpenSearchTemplateOperations openSearchTemplateOperations;
  final OpenSearchISMOperations openSearchISMOperations;
  BeanFactory beanFactory;
  OpenSearchClient openSearchClient;

  public RichOpenSearchClient(
      final BeanFactory beanFactory,
      final OpenSearchClient openSearchClient,
      final OpenSearchAsyncClient openSearchAsyncClient,
      @Qualifier("operateObjectMapper") final ObjectMapper objectMapper) {
    this.beanFactory = beanFactory;
    this.openSearchClient = openSearchClient;
    async = new Async(openSearchAsyncClient);
    openSearchBatchOperations =
        new OpenSearchBatchOperations(LOGGER, openSearchClient, beanFactory);
    openSearchClusterOperations = new OpenSearchClusterOperations(LOGGER, openSearchClient);
    openSearchDocumentOperations = new OpenSearchDocumentOperations(LOGGER, openSearchClient);
    openSearchIndexOperations =
        new OpenSearchIndexOperations(LOGGER, openSearchClient, objectMapper);
    openSearchPipelineOperations = new OpenSearchPipelineOperations(LOGGER, openSearchClient);
    openSearchSnapshotOperations = new OpenSearchSnapshotOperations(LOGGER, openSearchClient);
    openSearchTaskOperations = new OpenSearchTaskOperations(LOGGER, openSearchClient);
    openSearchTemplateOperations = new OpenSearchTemplateOperations(LOGGER, openSearchClient);
    openSearchISMOperations = new OpenSearchISMOperations(LOGGER, openSearchClient);
  }

  public Async async() {
    return async;
  }

  public OpenSearchBatchOperations batch() {
    return openSearchBatchOperations;
  }

  public OpenSearchClusterOperations cluster() {
    return openSearchClusterOperations;
  }

  public OpenSearchDocumentOperations doc() {
    return openSearchDocumentOperations;
  }

  public OpenSearchIndexOperations index() {
    return openSearchIndexOperations;
  }

  public OpenSearchISMOperations ism() {
    return openSearchISMOperations;
  }

  public OpenSearchPipelineOperations pipeline() {
    return openSearchPipelineOperations;
  }

  public OpenSearchSnapshotOperations snapshot() {
    return openSearchSnapshotOperations;
  }

  public OpenSearchTaskOperations task() {
    return openSearchTaskOperations;
  }

  public OpenSearchTemplateOperations template() {
    return openSearchTemplateOperations;
  }

  public record AggregationValue(String key, long count) {}

  public class Async {
    final OpenSearchAsyncSnapshotOperations openSearchAsyncSnapshotOperations;

    public Async(final OpenSearchAsyncClient openSearchAsyncClient) {
      openSearchAsyncSnapshotOperations =
          new OpenSearchAsyncSnapshotOperations(LOGGER, openSearchAsyncClient);
    }

    public OpenSearchAsyncSnapshotOperations snapshot() {
      return openSearchAsyncSnapshotOperations;
    }
  }
}
