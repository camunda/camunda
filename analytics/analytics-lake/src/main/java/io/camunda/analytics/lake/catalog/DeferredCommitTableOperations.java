/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.catalog;

import java.util.UUID;
import org.apache.iceberg.LocationProviders;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.TableMetadataParser;
import org.apache.iceberg.TableOperations;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.LocationProvider;

/**
 * A {@link TableOperations} that runs iceberg-core's normal commit pipeline but <em>defers</em> the
 * final pointer swap. {@code AppendFiles.commit()} (and every other update operation) ends by
 * calling {@link #commit(TableMetadata, TableMetadata)}; the standard implementations write the new
 * metadata file and then swap the catalog pointer. This one writes the file — staging is
 * write-ahead, the file must be durable before any pointer can ever reference it — and captures the
 * swap as a {@link StagedCommit} for {@link LakeCommitCoordinator} to execute later, batched with
 * other tables' swaps in one database transaction.
 *
 * <p>Not thread-safe and single-use by design: one instance stages one table's changes for one
 * coordinator batch, then is discarded. If the batch's transaction fails, the staged metadata file
 * is an unreferenced orphan (queued for sweep) and the caller re-stages from fresh state — nothing
 * here is retried in place.
 *
 * <p>Chained updates on the same staged table (two update operations before the batch commits) are
 * supported: the compare-and-swap predicate keeps the location the <em>first</em> update was based
 * on — the database has never seen the intermediate states, so the swap must be predicated on what
 * the database actually holds.
 */
final class DeferredCommitTableOperations implements TableOperations {

  private final TableOperations delegate;
  private final TableIdentifier identifier;

  private TableMetadata current;
  private StagedCommit staged;

  DeferredCommitTableOperations(final TableIdentifier identifier, final TableOperations delegate) {
    this.identifier = identifier;
    this.delegate = delegate;
    current = delegate.refresh();
    if (current == null) {
      throw new IllegalStateException(
          "Table %s does not exist yet — the coordinator batches commits to existing tables only"
              .formatted(identifier));
    }
  }

  /** The captured swap, or {@code null} if no update operation committed against this instance. */
  StagedCommit staged() {
    return staged;
  }

  @Override
  public TableMetadata current() {
    return current;
  }

  @Override
  public TableMetadata refresh() {
    // Once something is staged, "current" is the staged state (so chained updates build on it);
    // refreshing from the catalog would silently discard the staged snapshot.
    if (staged == null) {
      current = delegate.refresh();
    }
    return current;
  }

  @Override
  public void commit(final TableMetadata base, final TableMetadata metadata) {
    if (base != current) {
      // Same contract as the standard implementations: the caller built its update on a stale
      // base; iceberg-core reacts by refreshing and re-applying.
      throw new CommitFailedException(
          "Cannot stage commit for %s: base metadata is stale", identifier);
    }
    final String newLocation = newMetadataFileLocation(metadata);
    TableMetadataParser.write(metadata, io().newOutputFile(newLocation));
    final String expected =
        staged == null ? base.metadataFileLocation() : staged.expectedMetadataLocation();
    staged = new StagedCommit(identifier, expected, newLocation, metadata);
    current = metadata;
  }

  /**
   * Next metadata file location, following the standard {@code <version>-<uuid>.metadata.json}
   * naming so the staged file sits indistinguishably next to catalog-committed ones. The version is
   * parsed from the current location's file name; a parse failure only costs the
   * monotonically-increasing prefix (the UUID keeps the name unique), never correctness — the
   * catalog pointer, not the file name, decides what is current.
   */
  private String newMetadataFileLocation(final TableMetadata metadata) {
    final String fileName =
        "%05d-%s%s"
            .formatted(
                parseVersion(current.metadataFileLocation()) + 1,
                UUID.randomUUID(),
                TableMetadataParser.getFileExtension(TableMetadataParser.Codec.NONE));
    return delegate.metadataFileLocation(fileName);
  }

  private static int parseVersion(final String metadataLocation) {
    final int slash = metadataLocation.lastIndexOf('/');
    final int dash = metadataLocation.indexOf('-', slash + 1);
    if (dash < 0) {
      return 0;
    }
    try {
      return Integer.parseInt(metadataLocation.substring(slash + 1, dash));
    } catch (final NumberFormatException e) {
      return 0;
    }
  }

  @Override
  public FileIO io() {
    return delegate.io();
  }

  @Override
  public String metadataFileLocation(final String fileName) {
    return delegate.metadataFileLocation(fileName);
  }

  @Override
  public LocationProvider locationProvider() {
    return LocationProviders.locationsFor(current.location(), current.properties());
  }

  @Override
  public long newSnapshotId() {
    return delegate.newSnapshotId();
  }
}
