/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.dynamic.config.state.PhysicalTenantConfigReference;
import io.camunda.zeebe.dynamic.config.state.PhysicalTenantConfigReference.Owner;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.jspecify.annotations.NullMarked;

/**
 * The physical tenants that were created through the API, with the configuration each runs with, as
 * the cluster configuration and the members' configuration stores hold them. Nothing in the static
 * configuration describes these tenants, so a restore needs this to bring them back.
 *
 * <p>It is kept in the backup store of the default tenant, which the static configuration names, in
 * the backup metadata slot of partition {@value #METADATA_SLOT}: no tenant has a partition 0, so
 * the slot is free in every store type.
 */
@NullMarked
public record PhysicalTenantCatalogue(List<Entry> tenants) {

  public static final int METADATA_SLOT = 0;
  private static final ObjectMapper JSON = new ObjectMapper();

  public PhysicalTenantCatalogue {
    tenants = List.copyOf(tenants);
  }

  public byte[] toBytes() {
    try {
      return JSON.writeValueAsBytes(this);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static PhysicalTenantCatalogue fromBytes(final byte[] bytes) {
    try {
      return JSON.readValue(bytes, PhysicalTenantCatalogue.class);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The catalogue in the given backup store, if one was written to it. */
  public static Optional<PhysicalTenantCatalogue> load(final BackupStore store) {
    return store.loadBackupMetadata(METADATA_SLOT).join().map(PhysicalTenantCatalogue::fromBytes);
  }

  /**
   * One tenant created through the API.
   *
   * @param configuration the tenant's configuration blob, as stored in {@code
   *     physical-tenant-configs/<id>/<version>.config}
   */
  public record Entry(
      String id, long version, String sha256, String incarnationId, byte[] configuration) {

    /** The configuration as properties relative to {@code camunda.physical-tenants.<id>}. */
    public Map<String, Object> properties() {
      return PhysicalTenantConfigCodec.asProperties(
          PhysicalTenantConfigCodec.decode(configuration));
    }

    /** The reference the cluster configuration holds for the tenant, with no member applied. */
    public PhysicalTenantConfigReference reference() {
      return new PhysicalTenantConfigReference(
          version, sha256, Owner.API, incarnationId, new TreeMap<>());
    }
  }
}
