/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.file;

import static io.camunda.secretstore.SecretErrorCode.ACCESS_DENIED;
import static io.camunda.secretstore.SecretErrorCode.NOT_FOUND;
import static io.camunda.secretstore.SecretErrorCode.UNREADABLE;
import static java.util.stream.Collectors.toMap;

import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretStore;
import io.camunda.secretstore.SecretStoreUnavailableException;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A secret store backed by a directory where each file is one secret: the file name is the secret
 * name and the file contents (UTF-8, minus a single trailing newline) are the value.
 *
 * <p>This mirrors how Kubernetes projects a mounted Secret volume — one file per key — so it plugs
 * into k8s, External Secrets Operator, and Secrets Store CSI setups with no transformation. Hidden
 * entries (names starting with {@code .}, such as the {@code ..data} symlink k8s creates for atomic
 * updates) and non-regular files are treated as non-secrets in both {@link #list()} and {@link
 * #resolve(Set)}. Symlinks are followed when reading a value. Files are read on every call, so
 * rotated values are picked up without a restart.
 *
 * <p>Symlinks are followed only within the directory: a secret's resolved (symlink-followed) real
 * path must stay inside the configured directory, so an entry that resolves outside it (e.g. a
 * symlink to an arbitrary file) is ignored rather than read. The directory itself is trusted and
 * must be a restrictively-permissioned, single-tenant location — as a Kubernetes Secret {@code
 * tmpfs} mount is — so that only authorized processes can place entries in it.
 */
public final class FileBasedSecretStore implements SecretStore {

  private static final Logger LOG = LoggerFactory.getLogger(FileBasedSecretStore.class);

  private final Path directory;

  public FileBasedSecretStore(final Path directory) {
    this.directory = directory;
  }

  @Override
  public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
    if (names.isEmpty()) {
      return Map.of();
    }
    requireDirectory();
    LOG.debug("Resolving {} secret refs from '{}'", names.size(), directory);
    return names.stream().collect(toMap(name -> name, this::resolveOne));
  }

  @Override
  public List<String> list() {
    requireDirectory();
    final var refs = new ArrayList<String>();
    try (final DirectoryStream<Path> entries =
        Files.newDirectoryStream(directory, FileBasedSecretStore::isVisibleRegularFile)) {
      for (final Path entry : entries) {
        final var name = entry.getFileName().toString();
        if (!FileBasedSecretReference.isValid(name)) {
          LOG.warn("Skipping file with invalid secret name '{}' in '{}'", name, directory);
          continue;
        }
        if (!isWithinDirectory(entry)) {
          continue;
        }
        refs.add(name);
      }
    } catch (final IOException | SecurityException e) {
      throw new SecretStoreUnavailableException(
          "Failed to list secrets in '" + directory + "': " + e.getMessage(), e);
    }
    LOG.debug("Listing {} secrets from '{}'", refs.size(), directory);
    return refs;
  }

  private SecretResolutionResult resolveOne(final String name) {
    if (!FileBasedSecretReference.isValid(name)) {
      return notFound(name);
    }
    // name is validated to a single path segment, so this is always a direct child.
    final var file = directory.resolve(name);
    try {
      if (isHidden(name) || !Files.isRegularFile(file) || !isWithinDirectory(file)) {
        return notFound(name);
      }
      return new SecretResolutionResult.Resolved(readValue(file));
    } catch (final NoSuchFileException e) {
      // deleted between the check above and the read (e.g. rotation): treat as not found
      return notFound(name);
    } catch (final SecurityException e) {
      // one inaccessible secret must not abort the batch: surface it as a per-ref failure so the
      // other refs still resolve.
      return new SecretResolutionResult.Failed(
          ACCESS_DENIED, "Access denied reading secret: " + name, e);
    } catch (final IOException e) {
      // read or decode failure (e.g. malformed UTF-8) for this one secret: fail only this ref.
      return new SecretResolutionResult.Failed(UNREADABLE, "Failed to read secret: " + name, e);
    }
  }

  private void requireDirectory() {
    final boolean isDirectory;
    try {
      isDirectory = Files.isDirectory(directory);
    } catch (final SecurityException e) {
      throw new SecretStoreUnavailableException(
          "Cannot access secrets directory '" + directory + "': " + e.getMessage(), e);
    }
    if (!isDirectory) {
      throw new SecretStoreUnavailableException(
          "Secrets directory does not exist or is not a directory: " + directory);
    }
  }

  private static SecretResolutionResult notFound(final String name) {
    return new SecretResolutionResult.Failed(NOT_FOUND, "Secret not found: " + name, null);
  }

  /**
   * Returns whether {@code file}'s real (symlink-resolved) path stays inside the configured
   * directory. Name validation only guards the reference name, not where a symlink resolves to, so
   * this is the guard against an in-directory symlink pointing at an arbitrary file outside it.
   */
  private boolean isWithinDirectory(final Path file) throws IOException {
    final var withinDirectory = file.toRealPath().startsWith(directory.toRealPath());
    if (!withinDirectory) {
      LOG.warn(
          "Ignoring secret '{}' in '{}': it resolves to a path outside the secrets directory",
          file.getFileName(),
          directory);
    }
    return withinDirectory;
  }

  private static boolean isVisibleRegularFile(final Path path) {
    return !isHidden(path.getFileName().toString()) && Files.isRegularFile(path);
  }

  private static boolean isHidden(final String name) {
    return name.startsWith(".");
  }

  private static String readValue(final Path file) throws IOException {
    // readString decodes as UTF-8 and throws on malformed input instead of silently substituting.
    return stripSingleTrailingNewline(Files.readString(file));
  }

  private static String stripSingleTrailingNewline(final String value) {
    if (!value.endsWith("\n")) {
      return value;
    }
    // Drop the trailing LF, plus a preceding CR so a single CRLF terminator counts as one.
    final var end = value.length() - 1;
    if (end > 0 && value.charAt(end - 1) == '\r') {
      return value.substring(0, end - 1);
    }
    return value.substring(0, end);
  }
}
