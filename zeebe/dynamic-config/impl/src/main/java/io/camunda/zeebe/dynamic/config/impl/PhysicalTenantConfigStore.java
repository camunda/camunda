/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.impl;

import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Keeps the configurations of physical tenants on this member's disk, one file per tenant and
 * version, next to the persisted cluster configuration. The cluster configuration only refers to
 * them by version and hash, so a member must keep every version it may be asked for by a peer.
 */
@NullMarked
public final class PhysicalTenantConfigStore {

  static final String DIRECTORY_NAME = "physical-tenant-configs";

  private final @Nullable Path directory;
  private final Map<String, byte[]> inMemory = new ConcurrentHashMap<>();

  public PhysicalTenantConfigStore(final Path dataRootDirectory) {
    directory = dataRootDirectory.resolve(DIRECTORY_NAME);
  }

  private PhysicalTenantConfigStore() {
    directory = null;
  }

  /**
   * A store that keeps the configurations in memory only, for a node without a data directory, such
   * as a standalone gateway, which fetches what it needs again after a restart.
   */
  public static PhysicalTenantConfigStore inMemory() {
    return new PhysicalTenantConfigStore();
  }

  /** Stores the configuration durably; storing the same version again replaces it. */
  public void put(final String physicalTenantId, final long version, final byte[] configuration) {
    if (directory == null) {
      inMemory.put(physicalTenantId + "/" + version, configuration.clone());
      return;
    }
    final var file = file(physicalTenantId, version);
    try {
      FileUtil.ensureDirectoryExists(FileUtil.requireParent(file));
      final var temporary = file.resolveSibling(file.getFileName() + ".tmp");
      Files.write(temporary, configuration);
      FileUtil.flush(temporary);
      FileUtil.moveDurably(temporary, file, StandardCopyOption.ATOMIC_MOVE);
    } catch (final IOException e) {
      throw new UncheckedIOException(
          "Failed to store version %d of the configuration of physical tenant '%s'"
              .formatted(version, physicalTenantId),
          e);
    }
  }

  public Optional<byte[]> get(final String physicalTenantId, final long version) {
    if (directory == null) {
      return Optional.ofNullable(inMemory.get(physicalTenantId + "/" + version)).map(byte[]::clone);
    }
    try {
      return Optional.of(Files.readAllBytes(file(physicalTenantId, version)));
    } catch (final NoSuchFileException e) {
      return Optional.empty();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The stored configuration, if it is present and matches the hash. */
  public Optional<byte[]> get(
      final String physicalTenantId, final long version, final String sha256) {
    return get(physicalTenantId, version).filter(stored -> sha256(stored).equals(sha256));
  }

  public static String sha256(final byte[] configuration) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(configuration));
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private Path file(final String physicalTenantId, final long version) {
    return Objects.requireNonNull(directory).resolve(physicalTenantId).resolve(version + ".config");
  }
}
