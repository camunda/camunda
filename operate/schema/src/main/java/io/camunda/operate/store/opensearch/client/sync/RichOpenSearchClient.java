/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch.client.sync;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.store.opensearch.client.async.OpenSearchAsyncDocumentOperations;
import io.camunda.operate.store.opensearch.client.async.OpenSearchAsyncIndexOperations;
import io.camunda.operate.store.opensearch.client.async.OpenSearchAsyncSnapshotOperations;
import io.camunda.operate.store.opensearch.client.async.OpenSearchAsyncTaskOperations;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
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
  final OpenSearchTemplateOperations openSearchTemplateOperations;
  final OpenSearchISMOperations openSearchISMOperations;
  BeanFactory beanFactory;
  OpenSearchClient openSearchClient;

  public RichOpenSearchClient(
      final BeanFactory beanFactory,
      final OpenSearchClient openSearchClient,
      final OpenSearchAsyncClient openSearchAsyncClient) {
    this.beanFactory = beanFactory;
    this.openSearchClient = openSearchClient;
    async = new Async(openSearchAsyncClient);
    openSearchBatchOperations =
        new OpenSearchBatchOperations(LOGGER, openSearchClient, beanFactory);
    openSearchClusterOperations = new OpenSearchClusterOperations(LOGGER, openSearchClient);
    openSearchDocumentOperations = new OpenSearchDocumentOperations(LOGGER, openSearchClient);
    openSearchIndexOperations = new OpenSearchIndexOperations(LOGGER, openSearchClient);
    openSearchPipelineOperations = new OpenSearchPipelineOperations(LOGGER, openSearchClient);
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

  public OpenSearchTemplateOperations template() {
    return openSearchTemplateOperations;
  }

  public record AggregationValue(String key, long count) {}

  public class Async {
    final OpenSearchAsyncDocumentOperations openSearchAsyncDocumentOperations;
    final OpenSearchAsyncIndexOperations openSearchAsyncIndexOperations;
    final OpenSearchAsyncSnapshotOperations openSearchAsyncSnapshotOperations;
    final OpenSearchAsyncTaskOperations openSearchAsyncTaskOperations;

    public Async(final OpenSearchAsyncClient openSearchAsyncClient) {
      openSearchAsyncDocumentOperations =
          new OpenSearchAsyncDocumentOperations(LOGGER, openSearchAsyncClient);
      openSearchAsyncIndexOperations =
          new OpenSearchAsyncIndexOperations(LOGGER, openSearchAsyncClient);
      openSearchAsyncSnapshotOperations =
          new OpenSearchAsyncSnapshotOperations(LOGGER, openSearchAsyncClient);
      openSearchAsyncTaskOperations =
          new OpenSearchAsyncTaskOperations(LOGGER, openSearchAsyncClient);
    }

    public OpenSearchAsyncDocumentOperations doc() {
      return openSearchAsyncDocumentOperations;
    }

    public OpenSearchAsyncIndexOperations index() {
      return openSearchAsyncIndexOperations;
    }

    public OpenSearchAsyncSnapshotOperations snapshot() {
      return openSearchAsyncSnapshotOperations;
    }

    public OpenSearchAsyncTaskOperations task() {
      return openSearchAsyncTaskOperations;
    }
  }
}
