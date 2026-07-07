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
import io.camunda.configuration.Document.AwsStore;
import io.camunda.configuration.Document.AzureStore;
import io.camunda.configuration.Document.GcpStore;
import io.camunda.configuration.Document.InMemoryStore;
import io.camunda.configuration.Document.LocalStore;
import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.configuration.physicaltenants.MapOverlaySpec.MapDescriptor;
import io.camunda.configuration.physicaltenants.MapOverlaySpec.OverlayContext;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.core.env.Environment;

/**
 * Per-tenant {@link Document} resolution: the map merge itself is the generic {@link
 * PhysicalTenantMapOverlay} (registered via {@link #SPEC}); this class contributes only the
 * document-specific policy that rides on the spec's {@code postProcess} hook — store-id uniqueness
 * across provider types and narrowing the catalog to the tenant's {@code assigned} list.
 */
@NullMarked
public final class PhysicalTenantDocumentConfigurations {

  static final MapOverlaySpec<Document> SPEC =
      new MapOverlaySpec<>(
          "document",
          Document.class,
          Document::new,
          List.of(
              new MapDescriptor<Document, AwsStore>("aws", AwsStore.class, Document::getAws),
              new MapDescriptor<Document, GcpStore>("gcp", GcpStore.class, Document::getGcp),
              new MapDescriptor<Document, AzureStore>(
                  "azure", AzureStore.class, Document::getAzure),
              new MapDescriptor<Document, LocalStore>(
                  "local", LocalStore.class, Document::getLocal),
              new MapDescriptor<Document, InMemoryStore>(
                  "in-memory", InMemoryStore.class, Document::getInMemory)),
          PhysicalTenantDocumentConfigurations::postProcess,
          Camunda::setDocument);

  private PhysicalTenantDocumentConfigurations() {}

  public static Document forPhysicalTenant(final String tenantId, final Environment environment) {
    return PhysicalTenantMapOverlay.overlay(SPEC, tenantId, environment);
  }

  private static void postProcess(final OverlayContext context, final Document doc) {
    validateStoreIdUniqueness(context.tenantId(), doc);
    narrowToAssigned(context, doc);
  }

  private static void validateStoreIdUniqueness(final String tenantId, final Document doc) {
    final Set<String> seen = new LinkedHashSet<>();
    final Set<String> duplicates = new LinkedHashSet<>();
    for (final Set<String> keys :
        List.of(
            doc.getAws().keySet(),
            doc.getGcp().keySet(),
            doc.getAzure().keySet(),
            doc.getLocal().keySet(),
            doc.getInMemory().keySet())) {
      keys.forEach(
          id -> {
            final String normalizedId = Document.normalizeStoreId(id);
            if (!seen.add(normalizedId)) {
              duplicates.add(normalizedId);
            }
          });
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

  private static void narrowToAssigned(final OverlayContext context, final Document doc) {
    final List<String> assigned =
        context
            .binder()
            .bind(context.ptPrefix() + ".assigned", Bindable.listOf(String.class))
            .orElse(null);
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
      throw new UnifiedConfigurationException(
          "Physical tenant '"
              + context.tenantId()
              + "' sets 'default-store-id' to '"
              + defaultStoreId
              + "' but that store is not in its 'assigned' list "
              + assignedIds
              + ". Either include it in 'assigned' or remove 'default-store-id'.");
    }
  }
}
