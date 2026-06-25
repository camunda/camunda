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
 *
 * <p>Uses a snapshot-then-rebind strategy to avoid Spring's {@link Binder} replacing entire map
 * entries on partial field overrides. Root {@code assigned} is always cleared before the overlay so
 * tenants cannot inherit it.
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

    // clear `assigned` on root instance — it must never be inherited by tenants
    doc.setAssigned(new ArrayList<>());

    // snapshot pristine root store POJOs before the tenant overlay mutates them
    final Map<String, AwsStore> rootAws = new LinkedHashMap<>(doc.getAws());
    final Map<String, GcpStore> rootGcp = new LinkedHashMap<>(doc.getGcp());
    final Map<String, AzureStore> rootAzure = new LinkedHashMap<>(doc.getAzure());
    final Map<String, LocalStore> rootLocal = new LinkedHashMap<>(doc.getLocal());
    final Map<String, InMemoryStore> rootInMemory = new LinkedHashMap<>(doc.getInMemory());

    final String ptPrefix = PT_PREFIX_TEMPLATE.formatted(tenantId);
    binder.bind(ptPrefix, Bindable.ofInstance(doc));

    mergeSharedStores(doc, rootAws, rootGcp, rootAzure, rootLocal, rootInMemory, binder, ptPrefix);

    narrowToAssigned(doc);

    return doc;
  }

  /**
   * Re-binds the tenant overlay onto the pristine root POJOs for any store id that existed in both
   * the root catalog and the tenant overlay. Without this step, a tenant that overrides only some
   * fields of a shared store (e.g. only {@code bucketPath}) would lose all other root-level fields
   * (e.g. {@code bucketName}, {@code region}) because Spring's {@link Binder} replaces the entire
   * map entry on the first matching key.
   */
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
          if (doc.getAws().containsKey(storeId)) {
            binder.bind(ptPrefix + ".aws." + storeId, Bindable.ofInstance(rootStore));
            doc.getAws().put(storeId, rootStore);
          }
        });

    rootGcp.forEach(
        (storeId, rootStore) -> {
          if (doc.getGcp().containsKey(storeId)) {
            binder.bind(ptPrefix + ".gcp." + storeId, Bindable.ofInstance(rootStore));
            doc.getGcp().put(storeId, rootStore);
          }
        });

    rootAzure.forEach(
        (storeId, rootStore) -> {
          if (doc.getAzure().containsKey(storeId)) {
            binder.bind(ptPrefix + ".azure." + storeId, Bindable.ofInstance(rootStore));
            doc.getAzure().put(storeId, rootStore);
          }
        });

    rootLocal.forEach(
        (storeId, rootStore) -> {
          if (doc.getLocal().containsKey(storeId)) {
            binder.bind(ptPrefix + ".local." + storeId, Bindable.ofInstance(rootStore));
            doc.getLocal().put(storeId, rootStore);
          }
        });

    rootInMemory.forEach(
        (storeId, rootStore) -> {
          if (doc.getInMemory().containsKey(storeId)) {
            binder.bind(ptPrefix + ".in-memory." + storeId, Bindable.ofInstance(rootStore));
            doc.getInMemory().put(storeId, rootStore);
          }
        });
  }

  /**
   * Narrows the resolved document catalog to the ids listed in {@code assigned}. Store ids not in
   * the list are removed from all provider maps. If the current {@code defaultStoreId} was dropped,
   * it is reset to {@code null} so downstream code can select a sensible fallback.
   *
   * <p>A no-op when {@code assigned} is empty (meaning "no restriction — keep the full catalog").
   */
  private static void narrowToAssigned(final Document doc) {
    final List<String> assigned = doc.getAssigned();
    if (assigned.isEmpty()) {
      return;
    }

    final Set<String> assignedIds = new LinkedHashSet<>();
    for (final String id : assigned) {
      if (!id.isBlank()) {
        assignedIds.add(Document.normalizeStoreId(id));
      }
    }

    // The provider maps on `doc` must be mutable (LinkedHashMap — matches Document's field
    // initializer). retainAll modifies the map in-place; Spring's Binder preserves the
    // initializer's collection type, so this is safe with the current field declaration.
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
