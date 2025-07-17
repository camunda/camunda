/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.util;

import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.clients.reader.AuthorizationDocumentReader;
import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.search.clients.reader.GroupMemberDocumentReader;
import io.camunda.search.clients.reader.RoleMemberDocumentReader;
import io.camunda.search.clients.reader.TenantMemberDocumentReader;
import io.camunda.search.clients.reader.UserDocumentReader;
import io.camunda.search.clients.reader.UserReader;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.es.clients.ElasticsearchSearchClient;
import io.camunda.search.os.clients.OpensearchSearchClient;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.index.AuthorizationIndex;
import io.camunda.webapps.schema.descriptors.index.GroupIndex;
import io.camunda.webapps.schema.descriptors.index.RoleIndex;
import io.camunda.webapps.schema.descriptors.index.TenantIndex;
import io.camunda.webapps.schema.descriptors.index.UserIndex;

public class SearchClientsUtil {

  public static ElasticsearchSearchClient createLowLevelSearchClient(
      final String elasticsearchUrl) {
    final var config = new ConnectConfiguration();
    config.setUrl(elasticsearchUrl);
    final var elasticsearchClient = new ElasticsearchConnector(config).createClient();
    return new ElasticsearchSearchClient(elasticsearchClient);
  }

  public static UserReader createUserReader(final DocumentBasedSearchClient client) {
    final var indexDescriptors = new IndexDescriptors("", true);
    final var executor =
        new SearchClientBasedQueryExecutor(
            client, ServiceTransformers.newInstance(indexDescriptors));
    final var roleMemberReader =
        new RoleMemberDocumentReader(executor, indexDescriptors.get(RoleIndex.class));
    final var tenantMemberReader =
        new TenantMemberDocumentReader(executor, indexDescriptors.get(TenantIndex.class));
    final var groupMemberReader =
        new GroupMemberDocumentReader(executor, indexDescriptors.get(GroupIndex.class));
    return new UserDocumentReader(
        executor,
        indexDescriptors.get(UserIndex.class),
        roleMemberReader,
        tenantMemberReader,
        groupMemberReader);
  }

  public static AuthorizationReader createAuthorizationReader(
      final DocumentBasedSearchClient client) {
    final var indexDescriptors = new IndexDescriptors("", true);
    return new AuthorizationDocumentReader(
        new SearchClientBasedQueryExecutor(
            client, ServiceTransformers.newInstance(indexDescriptors)),
        indexDescriptors.get(AuthorizationIndex.class));
  }

  public static OpensearchSearchClient createLowLevelOpensearchSearchClient(
      final ConnectConfiguration config) {
    final var opensearchClient = new OpensearchConnector(config).createClient();
    return new OpensearchSearchClient(opensearchClient);
  }

  public static ElasticsearchSearchClient createLowLevelElasticsearchSearchClient(
      final ConnectConfiguration config) {
    final var elasticsearchClient = new ElasticsearchConnector(config).createClient();
    return new ElasticsearchSearchClient(elasticsearchClient);
  }
}
