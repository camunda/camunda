/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.Document;
import io.camunda.configuration.UnifiedConfigurationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;

/**
 * Cross-tenant rule: no two physical tenants may occupy overlapping document store key spaces, or
 * they would read and write into the same backing storage.
 *
 * <p>Overlap is broader than equality, because document ids are caller-supplied and concatenated
 * onto the key prefix unchecked. With prefixes {@code tenant} and {@code tenant-b-} in one bucket,
 * a request against the first store for document id {@code -b-invoice} resolves to the second
 * store's {@code tenant-b-invoice}, and deleting it requires nothing further. Adding a separator
 * changes nothing: {@code docs/} reaches {@code docs/archive/} through the id {@code
 * archive/invoice}, since no object store treats {@code /} in a document id as a path boundary. Any
 * prefix nested inside another tenant's is therefore rejected rather than trusted to isolate.
 */
@NullMarked
class DocumentStoreIsolationValidation implements CrossTenantValidation {

  @Override
  public void validate(final Map<String, Camunda> resolvedByTenant) {
    if (resolvedByTenant.size() <= 1) {
      return;
    }

    final List<TenantStore> stores =
        resolvedByTenant.entrySet().stream()
            .flatMap(tenant -> storesOf(tenant.getKey(), tenant.getValue()))
            .toList();

    final List<String> conflicts = new ArrayList<>(sharedLocations(stores));
    conflicts.addAll(enclosedLocations(stores));

    if (!conflicts.isEmpty()) {
      throw new UnifiedConfigurationException(
          "Physical tenants must not share a document store location, or they would read and write "
              + "into the same backing storage. Use a distinct bucket, container, or path per "
              + "tenant, and never nest one tenant's path inside another's — a nested path is "
              + "reachable through a caller-supplied document id, which no object store bounds at "
              + "'/'. Conflicts: "
              + String.join("; ", conflicts));
    }
  }

  /** In-memory stores are omitted: ephemeral and process-local, they cannot collide. */
  private static Stream<TenantStore> storesOf(final String tenantId, final Camunda camunda) {
    final Document doc = camunda.getDocument();
    return Stream.of(
            doc.getAws().values().stream().map(DocumentStoreLocation::aws),
            doc.getGcp().values().stream().map(DocumentStoreLocation::gcp),
            doc.getAzure().values().stream().map(DocumentStoreLocation::azure),
            doc.getLocal().values().stream().map(DocumentStoreLocation::local))
        .flatMap(Function.identity())
        .map(location -> new TenantStore(tenantId, location));
  }

  /**
   * One message per location rather than one per pair of tenants: a location shared by five tenants
   * is a single misconfiguration, and reporting its ten pairs would bury every other conflict.
   */
  private static List<String> sharedLocations(final List<TenantStore> stores) {
    final Map<DocumentStoreLocation, Set<String>> tenantsByLocation = new LinkedHashMap<>();
    stores.forEach(
        store ->
            tenantsByLocation
                .computeIfAbsent(store.location(), location -> new LinkedHashSet<>())
                .add(store.tenantId()));
    return tenantsByLocation.entrySet().stream()
        .filter(location -> location.getValue().size() > 1)
        .map(
            location ->
                String.format(
                    "tenants %s share the same document store location [%s]",
                    location.getValue(), location.getKey().describe()))
        .toList();
  }

  /**
   * The overlaps that are not one location: each names the enclosing tenant first, since it is the
   * one able to reach the other's documents. Pairwise, because which tenant encloses which is the
   * substance of the message. Identical locations are left to {@link #sharedLocations}.
   */
  private static List<String> enclosedLocations(final List<TenantStore> stores) {
    final Set<String> conflicts = new LinkedHashSet<>();
    for (int i = 0; i < stores.size(); i++) {
      for (int j = i + 1; j < stores.size(); j++) {
        final TenantStore first = stores.get(i);
        final TenantStore second = stores.get(j);
        if (!first.conflictsWith(second) || first.location().equals(second.location())) {
          continue;
        }
        final boolean firstEncloses =
            first.location().keyPrefix().length() < second.location().keyPrefix().length();
        final TenantStore enclosing = firstEncloses ? first : second;
        final TenantStore enclosed = firstEncloses ? second : first;
        conflicts.add(
            String.format(
                "tenant %s's document store location [%s] encloses tenant %s's [%s]",
                enclosing.tenantId(),
                enclosing.location().describe(),
                enclosed.tenantId(),
                enclosed.location().describe()));
      }
    }
    return List.copyOf(conflicts);
  }

  private record TenantStore(String tenantId, DocumentStoreLocation location) {

    /** Overlap within one tenant is not a leak: a tenant may spread documents across its stores. */
    boolean conflictsWith(final TenantStore other) {
      return !tenantId.equals(other.tenantId) && location.sharesKeySpaceWith(other.location);
    }
  }
}
