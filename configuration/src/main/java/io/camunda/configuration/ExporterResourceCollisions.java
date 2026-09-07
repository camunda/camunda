/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.zeebe.exporter.api.ExporterConfigMerger.ExporterIsolationClaim;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ExporterResourceCollisions {

  private ExporterResourceCollisions() {}

  public static final class Accumulator {

    private final Map<ResourceIdentity, ClaimedResource> ownersByResource = new LinkedHashMap<>();

    public void add(final String ownerId, final ExporterIsolationClaim claim) {
      ownersByResource
          .computeIfAbsent(
              ResourceIdentity.of(claim), k -> new ClaimedResource(claim.description()))
          .ownerIds()
          .add(ownerId);
    }

    public List<String> collisions() {
      final List<String> collisions = new ArrayList<>();
      ownersByResource.forEach(
          (identity, resource) -> {
            if (resource.ownerIds().size() > 1) {
              collisions.add(
                  String.format(
                      "owners %s share the same %s", resource.ownerIds(), resource.description()));
            }
          });
      return collisions;
    }
  }

  /** The collision identity of a claimed resource: owners collide iff both fields are equal. */
  private record ResourceIdentity(String domain, Map<String, Object> identity) {

    static ResourceIdentity of(final ExporterIsolationClaim claim) {
      return new ResourceIdentity(
          claim.domain(), ExporterArgsMergers.immutableCopy(claim.identity()));
    }
  }

  /** The owners claiming one resource, plus a human rendering of it for the error message. */
  private record ClaimedResource(String description, Set<String> ownerIds) {
    ClaimedResource(final String description) {
      this(description, new LinkedHashSet<>());
    }
  }
}
