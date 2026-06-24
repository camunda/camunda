/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.identity;

import io.camunda.db.rdbms.read.service.PersistentWebSessionDbReader;
import io.camunda.db.rdbms.read.service.PersistentWebSessionRdbmsClient;
import io.camunda.db.rdbms.write.RdbmsMapperBundle;
import io.camunda.db.rdbms.write.service.PersistentWebSessionWriter;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.DocumentBasedWriteClient;
import io.camunda.search.clients.PersistentWebSessionClient;
import io.camunda.search.clients.PersistentWebSessionSearchImpl;
import io.camunda.search.clients.PhysicalTenantScopedPersistentWebSessionClient;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.index.PersistentWebSessionIndexDescriptor;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the per-physical-tenant {@link PhysicalTenantScopedPersistentWebSessionClient} from
 * secondary-storage inputs, keeping the construction logic out of {@link
 * WebSessionRepositoryConfiguration}. One {@link PersistentWebSessionClient} is created per
 * physical tenant from that tenant's storage handles.
 */
public final class PhysicalTenantScopedPersistentWebSessionClientFactory {

  private PhysicalTenantScopedPersistentWebSessionClientFactory() {}

  /**
   * Builds a provider for Elasticsearch/OpenSearch: one {@link PersistentWebSessionSearchImpl} per
   * physical tenant from that tenant's document search client and index descriptors.
   *
   * @throws IllegalStateException if a tenant has no {@link IndexDescriptors}, or its search client
   *     does not also implement {@link DocumentBasedWriteClient}
   */
  public static PhysicalTenantScopedPersistentWebSessionClient fromDocumentSearchClients(
      final Map<String, DocumentBasedSearchClient> physicalTenantDocumentSearchClients,
      final Map<String, IndexDescriptors> physicalTenantScopedIndexDescriptors) {
    final var byTenant = new LinkedHashMap<String, PersistentWebSessionClient>();
    physicalTenantDocumentSearchClients.forEach(
        (tenantId, client) -> {
          final var descriptors = physicalTenantScopedIndexDescriptors.get(tenantId);
          if (descriptors == null) {
            throw new IllegalStateException(
                "Missing IndexDescriptors for physical tenant '" + tenantId + "'");
          }
          if (!(client instanceof final DocumentBasedWriteClient writeClient)) {
            throw new IllegalStateException(
                "Search client for physical tenant '"
                    + tenantId
                    + "' does not implement DocumentBasedWriteClient: "
                    + client.getClass().getName());
          }
          final var descriptor = descriptors.get(PersistentWebSessionIndexDescriptor.class);
          byTenant.put(
              tenantId, new PersistentWebSessionSearchImpl(client, writeClient, descriptor));
        });
    return new PhysicalTenantScopedPersistentWebSessionClient(Map.copyOf(byTenant));
  }

  /**
   * Builds a provider for RDBMS: one {@link PersistentWebSessionRdbmsClient} per physical tenant
   * from that tenant's {@link RdbmsMapperBundle}.
   */
  public static PhysicalTenantScopedPersistentWebSessionClient fromRdbmsMapperBundles(
      final Map<String, RdbmsMapperBundle> rdbmsMapperBundles) {
    final var byTenant = new LinkedHashMap<String, PersistentWebSessionClient>();
    rdbmsMapperBundles.forEach(
        (tenantId, bundle) -> {
          final var mapper = bundle.persistentWebSessionMapper();
          byTenant.put(
              tenantId,
              new PersistentWebSessionRdbmsClient(
                  new PersistentWebSessionDbReader(mapper),
                  new PersistentWebSessionWriter(mapper)));
        });
    return new PhysicalTenantScopedPersistentWebSessionClient(Map.copyOf(byTenant));
  }
}
