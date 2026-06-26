/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.Document;
import io.camunda.configuration.Document.AwsStore;
import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.configuration.Document.AzureStore;
import io.camunda.configuration.Document.GcpStore;
import io.camunda.configuration.Document.InMemoryStore;
import io.camunda.configuration.Document.LocalStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * Resolves a per-tenant {@link Document} by overlaying {@code
 * camunda.physical-tenants.<id>.document.*} on top of the root {@code camunda.document.*} catalog.
 * Uses a snapshot-then-rebind strategy so a tenant that overrides only some fields of a shared
 * store doesn't silently lose the root fields it didn't override.
 */
@NullMarked
public final class PhysicalTenantDocumentConfigurations {

  private static final String ROOT_PREFIX = "camunda.document";
  private static final String PT_PREFIX_TEMPLATE = "camunda.physical-tenants.%s.document";

  private PhysicalTenantDocumentConfigurations() {}

  public static Document forPhysicalTenant(final String tenantId, final Environment environment) {
    final Binder binder = Binder.get(environment);

    final Document doc =
        binder.bind(ROOT_PREFIX, Bindable.of(Document.class)).orElseGet(Document::new);

    final Map<String, AwsStore> rootAws = new LinkedHashMap<>(doc.getAws());
    final Map<String, GcpStore> rootGcp = new LinkedHashMap<>(doc.getGcp());
    final Map<String, AzureStore> rootAzure = new LinkedHashMap<>(doc.getAzure());
    final Map<String, LocalStore> rootLocal = new LinkedHashMap<>(doc.getLocal());
    final Map<String, InMemoryStore> rootInMemory = new LinkedHashMap<>(doc.getInMemory());

    final String ptPrefix = PT_PREFIX_TEMPLATE.formatted(tenantId);
    binder.bind(ptPrefix, Bindable.ofInstance(doc));

    mergeSharedStores(doc, rootAws, rootGcp, rootAzure, rootLocal, rootInMemory, binder, ptPrefix);

    validateStoreIdUniqueness(tenantId, doc);

    narrowToAssigned(doc, binder, ptPrefix);

    return doc;
  }

  private static void mergeSharedStores(
      final Document doc,
      final Map<String, AwsStore> rootAws,
      final Map<String, GcpStore> rootGcp,
      final Map<String, AzureStore> rootAzure,
      final Map<String, LocalStore> rootLocal,
      final Map<String, InMemoryStore> rootInMemory,
      final Binder binder,
      final String ptPrefix) {

    rootAws.forEach(
        (storeId, rootStore) -> {
          binder.bind(ptPrefix + ".aws." + storeId, Bindable.ofInstance(rootStore));
          doc.getAws().put(storeId, rootStore);
        });

    rootGcp.forEach(
        (storeId, rootStore) -> {
          binder.bind(ptPrefix + ".gcp." + storeId, Bindable.ofInstance(rootStore));
          doc.getGcp().put(storeId, rootStore);
        });

    rootAzure.forEach(
        (storeId, rootStore) -> {
          binder.bind(ptPrefix + ".azure." + storeId, Bindable.ofInstance(rootStore));
          doc.getAzure().put(storeId, rootStore);
        });

    rootLocal.forEach(
        (storeId, rootStore) -> {
          binder.bind(ptPrefix + ".local." + storeId, Bindable.ofInstance(rootStore));
          doc.getLocal().put(storeId, rootStore);
        });

    rootInMemory.forEach(
        (storeId, rootStore) -> {
          binder.bind(ptPrefix + ".in-memory." + storeId, Bindable.ofInstance(rootStore));
          doc.getInMemory().put(storeId, rootStore);
        });
  }

  private static void validateStoreIdUniqueness(final String tenantId, final Document doc) {
    final Set<String> seen = new LinkedHashSet<>();
    final List<String> duplicates = new ArrayList<>();
    for (final Set<String> keys :
        List.of(
            doc.getAws().keySet(),
            doc.getGcp().keySet(),
            doc.getAzure().keySet(),
            doc.getLocal().keySet(),
            doc.getInMemory().keySet())) {
      keys.forEach(id -> { if (!seen.add(id)) duplicates.add(id); });
    }
    if (!duplicates.isEmpty()) {
      throw new UnifiedConfigurationException(
          "Physical tenant '"
              + tenantId
              + "' has duplicate document store id(s) across provider types: "
              + duplicates
              + ". Store ids must be unique across aws, gcp, azure, local, and in-memory.");
    }
  }

  private static void narrowToAssigned(
      final Document doc, final Binder binder, final String ptPrefix) {
    final List<String> assigned =
        binder.bind(ptPrefix + ".assigned", Bindable.listOf(String.class)).orElse(null);
    if (assigned == null || assigned.isEmpty()) {
      return;
    }

    final Set<String> assignedIds = new LinkedHashSet<>();
    for (final String id : assigned) {
      if (!id.isBlank()) {
        assignedIds.add(Document.normalizeStoreId(id));
      }
    }

    // provider maps are LinkedHashMap (Document's field initializer) — retainAll is safe
    doc.getAws().keySet().retainAll(assignedIds);
    doc.getGcp().keySet().retainAll(assignedIds);
    doc.getAzure().keySet().retainAll(assignedIds);
    doc.getLocal().keySet().retainAll(assignedIds);
    doc.getInMemory().keySet().retainAll(assignedIds);

    final String defaultStoreId = doc.getDefaultStoreId();
    if (defaultStoreId != null && !assignedIds.contains(defaultStoreId)) {
      doc.setDefaultStoreId(null);
    }
  }
}
