/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.backup.repository.opensearch;

import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import java.util.List;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.snapshot.CreateSnapshotRequest;
import org.opensearch.client.opensearch.snapshot.DeleteSnapshotRequest;
import org.opensearch.client.opensearch.snapshot.GetRepositoryRequest;
import org.opensearch.client.opensearch.snapshot.GetSnapshotRequest;

/** Copied from operate RequestDSL interface */
public interface OpensearchRequestDSL {
  private static String whereToSearch(
      final IndexTemplateDescriptor template, final QueryType queryType) {
    return switch (queryType) {
      case ONLY_RUNTIME -> template.getFullQualifiedName();
      case ALL -> template.getAlias();
    };
  }

  static CreateSnapshotRequest.Builder createSnapshotRequestBuilder(
      final String repository, final String snapshot, final List<String> indices) {
    return new CreateSnapshotRequest.Builder()
        .repository(repository)
        .snapshot(snapshot)
        .indices(indices);
  }

  static DeleteSnapshotRequest.Builder deleteSnapshotRequestBuilder(
      final String repositoryName, final String snapshotName) {
    return new DeleteSnapshotRequest.Builder().repository(repositoryName).snapshot(snapshotName);
  }

  static GetRepositoryRequest.Builder repositoryRequestBuilder(final String name) {
    return new GetRepositoryRequest.Builder().name(name);
  }

  static SearchRequest.Builder searchRequestBuilder(final String index) {
    return new SearchRequest.Builder().index(index);
  }

  static SearchRequest.Builder searchRequestBuilder(
      final IndexTemplateDescriptor template, final QueryType queryType) {
    final SearchRequest.Builder builder = new SearchRequest.Builder();
    builder.index(whereToSearch(template, queryType));
    return builder;
  }

  static SearchRequest.Builder searchRequestBuilder(final IndexTemplateDescriptor template) {
    return searchRequestBuilder(template, QueryType.ALL);
  }

  static GetSnapshotRequest.Builder getSnapshotRequestBuilder(
      final String repository, final String snapshot) {
    return new GetSnapshotRequest.Builder().repository(repository).snapshot(snapshot);
  }

  static GetRequest getRequest(final String index, final String id) {
    return new GetRequest.Builder().index(index).id(id).build();
  }

  static Time time(final String value) {
    return Time.of(b -> b.time(value));
  }

  enum QueryType {
    ONLY_RUNTIME,
    ALL
  }
}
