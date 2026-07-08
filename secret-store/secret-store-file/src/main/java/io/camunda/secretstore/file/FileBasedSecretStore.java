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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FileBasedSecretStore implements SecretStore<FileBasedSecretReference> {

  private static final Logger LOG = LoggerFactory.getLogger(FileBasedSecretStore.class);

  private final Path filePath;

  public FileBasedSecretStore(final Path filePath) {
    this.filePath = filePath;
  }

  @Override
  public Map<FileBasedSecretReference, SecretResolutionResult> resolve(
      final Set<FileBasedSecretReference> refs) {
    if (refs.isEmpty()) {
      return Map.of();
    }
    final var props = loadProperties();
    LOG.debug("Resolving {} secret refs from '{}'", refs.size(), filePath);
    return refs.stream()
        .collect(
            toMap(
                ref -> ref,
                ref -> {
                  final var value = props.getProperty(ref.name());
                  return value != null
                      ? new SecretResolutionResult.Resolved(value)
                      : new SecretResolutionResult.Failed(
                          NOT_FOUND, "Secret not found: " + ref.name(), null);
                }));
  }

  @Override
  public Collection<FileBasedSecretReference> list() {
    final var props = loadProperties();
    final var refs =
        props.stringPropertyNames().stream()
            .filter(name -> !name.isBlank())
            .map(FileBasedSecretReference::new)
            .toList();
    LOG.debug("Listing {} secrets from '{}'", refs.size(), filePath);
    return refs;
  }

  private Properties loadProperties() {
    final var props = new Properties();
    try (final var reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
      props.load(reader);
    } catch (final IOException e) {
      throw new SecretStoreUnavailableException(
          "Failed to load secrets file '" + filePath + "': " + e.getMessage(), e);
    } catch (final IllegalArgumentException e) {
      throw new SecretStoreUnavailableException(
          "Malformed secrets file '" + filePath + "': " + e.getMessage(), e);
    }
    return props;
  }
}
