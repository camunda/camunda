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
 * SPIKE (POC) — derives a merged {@link Document} configuration for a specific physical tenant
 * (catalog-with-inheritance). Mirrors the OIDC provider overlay ({@code
 * PhysicalTenantAuthConfigurations} in the {@code authentication} module) — same steps, same names
 * — to keep one mental model across OIDC, documents, and (future) exporters. The two cannot share
 * code: OIDC lives in {@code authentication/} because CSL is an external library, whereas the
 * resolved per-tenant {@link Document} is exactly what consumers read here in {@code
 * configuration/}.
 *
 * <p>Resolution per tenant:
 *
 * <ol>
 *   <li><b>Union</b> — root-declared stores ∪ the tenant's own stores. The Spring {@link Binder}
 *       binds the root {@code camunda.document.*} into a fresh {@link Document}, then binds the
 *       tenant overlay {@code camunda.physical-tenants.<id>.document.*} into the <em>same</em>
 *       instance, key-merging the per-provider maps.
 *   <li><b>Field-override of shared ids</b> — the overlay bind <em>replaces</em> the value POJO of
 *       any store id present on both sides (the {@code Map}-value footgun characterized for
 *       exporters), dropping root's sibling fields. {@link #mergeSharedStores} repairs that by
 *       re-binding the overlay onto each <em>pristine</em> root store POJO (tenant value wins per
 *       field; omitted fields inherit root). Clean here with no deep-merge because document stores
 *       are pure typed POJOs with no free-form map field.
 *   <li><b>Optional restriction</b> — {@link #narrowToAssigned} drops every store id absent from a
 *       tenant's {@code document.assigned} list (least privilege). Optional, unlike OIDC's
 *       mandatory {@code assigned}: an inherited store yields the tenant its own isolated location,
 *       not access to another tenant's data, and the cross-tenant collision check is the real
 *       guardrail.
 *   <li><b>Tenant-private stores</b> survive the union automatically (root-only and tenant-only
 *       stores need no repair).
 * </ol>
 *
 * <p>Cross-tenant location isolation is enforced separately, after all tenants resolve, by {@code
 * DocumentStoreIsolationValidation}.
 */
@NullMarked
public final class PhysicalTenantDocumentConfigurations {

  private static final String ROOT_PREFIX = "camunda.document";
  private static final String PT_PREFIX_TEMPLATE = "camunda.physical-tenants.%s.document";

  private PhysicalTenantDocumentConfigurations() {}

  /**
   * Produces the merged {@link Document} for the given physical tenant id: the union of root and
   * tenant stores, shared ids field-merged, narrowed to the tenant's optional {@code assigned}
   * selection.
   *
   * @param tenantId the physical tenant id (e.g. {@code "tenanta"})
   * @param environment the Spring {@link Environment} for binding root and overlay config
   * @return the resolved per-tenant {@link Document}
   */
  public static Document forPhysicalTenant(final String tenantId, final Environment environment) {
    final Binder binder = Binder.get(environment);
    final String ptPrefix = PT_PREFIX_TEMPLATE.formatted(tenantId);

    // Bind the root catalog into a fresh, per-call instance (safe to mutate below).
    final Document doc =
        binder.bind(ROOT_PREFIX, Bindable.of(Document.class)).orElseGet(Document::new);

    // `assigned` is meaningful only on a tenant overlay; discard anything declared at the root so
    // the overlay bind is the sole source of the restriction.
    doc.setAssigned(new ArrayList<>());

    // Snapshot the pristine root store POJOs before the overlay bind can replace shared ids.
    final Map<String, AwsStore> rootAws = new LinkedHashMap<>(doc.getAws());
    final Map<String, GcpStore> rootGcp = new LinkedHashMap<>(doc.getGcp());
    final Map<String, AzureStore> rootAzure = new LinkedHashMap<>(doc.getAzure());
    final Map<String, LocalStore> rootLocal = new LinkedHashMap<>(doc.getLocal());
    final Map<String, InMemoryStore> rootInMemory = new LinkedHashMap<>(doc.getInMemory());

    // Bind the overlay into the SAME instance: scalars override, provider maps key-merge (but
    // replace the value POJO of any shared store id — repaired next).
    binder.bind(ptPrefix, Bindable.ofInstance(doc));

    // Repair shared store ids: re-bind the overlay onto each pristine root POJO so fields the
    // overlay omitted survive (override + inherit, all by Binder). Root-only and tenant-only stores
    // need no repair.
    mergeSharedStores(binder, ptPrefix, "aws", rootAws, doc.getAws());
    mergeSharedStores(binder, ptPrefix, "gcp", rootGcp, doc.getGcp());
    mergeSharedStores(binder, ptPrefix, "azure", rootAzure, doc.getAzure());
    mergeSharedStores(binder, ptPrefix, "local", rootLocal, doc.getLocal());
    mergeSharedStores(binder, ptPrefix, "in-memory", rootInMemory, doc.getInMemory());

    narrowToAssigned(doc);
    return doc;
  }

  /**
   * Repairs store ids present on both root and overlay. The overlay bind replaced each such value
   * with a fresh POJO carrying only the overlay's keys; here we re-bind the overlay onto the
   * <em>pristine</em> root POJO so the fields the overlay omitted survive. Root-only and
   * tenant-only stores need no repair. Generic over the provider's store POJO type.
   */
  private static <T> void mergeSharedStores(
      final Binder binder,
      final String ptPrefix,
      final String providerKey,
      final Map<String, T> rootSnapshot,
      final Map<String, T> merged) {
    rootSnapshot.forEach(
        (id, rootStore) -> {
          if (merged.containsKey(id)) {
            binder.bind(ptPrefix + "." + providerKey + "." + id, Bindable.ofInstance(rootStore));
            merged.put(id, rootStore);
          }
        });
  }

  /**
   * Narrows the merged union to the store ids a tenant has explicitly selected via {@code
   * document.assigned} (least privilege). A tenant with no {@code assigned} list keeps the full
   * union — selection is optional. Store ids live in a single flat id space across providers, so
   * the selection is applied to every provider map. Simpler than OIDC: documents have no unnamed
   * default slot, so OIDC's reserved-id handling does not carry over.
   */
  private static void narrowToAssigned(final Document doc) {
    final List<String> assigned = doc.getAssigned();
    if (assigned == null || assigned.isEmpty()) {
      return; // no restriction — keep the full union
    }
    // Defensive: tolerate null/blank list elements (some yaml list shapes bind them).
    final Set<String> assignedIds = new LinkedHashSet<>();
    for (final String id : assigned) {
      if (id != null && !id.isBlank()) {
        assignedIds.add(id);
      }
    }
    doc.getAws().keySet().removeIf(id -> !assignedIds.contains(id));
    doc.getGcp().keySet().removeIf(id -> !assignedIds.contains(id));
    doc.getAzure().keySet().removeIf(id -> !assignedIds.contains(id));
    doc.getLocal().keySet().removeIf(id -> !assignedIds.contains(id));
    doc.getInMemory().keySet().removeIf(id -> !assignedIds.contains(id));
  }
}
