/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.file;

import static io.camunda.secretstore.SecretErrorCode.NOT_FOUND;
import static java.util.stream.Collectors.toMap;

import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretStore;
import io.camunda.secretstore.SecretStoreUnavailableException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
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
 * updates) and non-regular files are ignored when listing. Files are read on every call, so rotated
 * values are picked up without a restart.
 */
public final class FileBasedSecretStore implements SecretStore<FileBasedSecretReference> {

  private static final Logger LOG = LoggerFactory.getLogger(FileBasedSecretStore.class);

  private final Path directory;

  public FileBasedSecretStore(final Path directory) {
    this.directory = directory;
  }

  @Override
  public Map<FileBasedSecretReference, SecretResolutionResult> resolve(
      final Set<FileBasedSecretReference> refs) {
    if (refs.isEmpty()) {
      return Map.of();
    }
    requireDirectory();
    LOG.debug("Resolving {} secret refs from '{}'", refs.size(), directory);
    return refs.stream().collect(toMap(ref -> ref, this::resolveOne));
  }

  @Override
  public Collection<FileBasedSecretReference> list() {
    requireDirectory();
    final var refs = new ArrayList<FileBasedSecretReference>();
    try (final DirectoryStream<Path> entries =
        Files.newDirectoryStream(directory, FileBasedSecretStore::isVisibleRegularFile)) {
      for (final Path entry : entries) {
        refs.add(new FileBasedSecretReference(entry.getFileName().toString()));
      }
    } catch (final IOException e) {
      throw new SecretStoreUnavailableException(
          "Failed to list secrets in '" + directory + "': " + e.getMessage(), e);
    }
    LOG.debug("Listing {} secrets from '{}'", refs.size(), directory);
    return refs;
  }

  private SecretResolutionResult resolveOne(final FileBasedSecretReference ref) {
    // ref.name() is validated to be a single path segment, so this is always a direct child.
    final var file = directory.resolve(ref.name());
    if (!Files.isRegularFile(file)) {
      return new SecretResolutionResult.Failed(NOT_FOUND, "Secret not found: " + ref.name(), null);
    }
    try {
      return new SecretResolutionResult.Resolved(readValue(file));
    } catch (final IOException e) {
      throw new SecretStoreUnavailableException(
          "Failed to read secret '" + ref.name() + "' in '" + directory + "': " + e.getMessage(),
          e);
    }
  }

  private void requireDirectory() {
    if (!Files.isDirectory(directory)) {
      throw new SecretStoreUnavailableException(
          "Secrets directory does not exist or is not a directory: " + directory);
    }
  }

  private static boolean isVisibleRegularFile(final Path path) {
    final var name = path.getFileName().toString();
    return !name.startsWith(".") && Files.isRegularFile(path);
  }

  private static String readValue(final Path file) throws IOException {
    final var raw = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    return stripSingleTrailingNewline(raw);
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
