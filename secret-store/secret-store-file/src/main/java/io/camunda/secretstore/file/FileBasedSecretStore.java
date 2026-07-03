/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.file;

import static io.camunda.secretstore.SecretErrorCode.NOT_FOUND;
import static io.camunda.secretstore.SecretErrorCode.STORE_UNAVAILABLE;
import static java.util.stream.Collectors.toMap;

import io.camunda.secretstore.SecretRef;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NullMarked
public final class FileBasedSecretStore implements SecretStore {

  private static final Logger LOG = LoggerFactory.getLogger(FileBasedSecretStore.class);

  private final Path filePath;

  public FileBasedSecretStore(final Path filePath) {
    this.filePath = filePath;
  }

  @Override
  public Map<SecretRef, SecretResolutionResult> resolve(final Set<SecretRef> refs) {
    final Properties props;
    try {
      props = loadProperties();
    } catch (final UncheckedIOException e) {
      final var cause = e.getCause();
      final var causeMessage = cause != null ? cause.getMessage() : e.getMessage();
      LOG.warn("Failed to load secrets file '{}': {}", filePath, causeMessage);
      final var msg = "Failed to load secrets file: " + causeMessage;
      return refs.stream()
          .collect(
              toMap(
                  ref -> ref,
                  ref -> new SecretResolutionResult.Failed(STORE_UNAVAILABLE, msg, cause)));
    }
    // Never log resolved values — only ref names and counts are safe to log
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
  public Collection<SecretRef> list() {
    final var props = loadProperties();
    LOG.debug("Listing {} secrets from '{}'", props.size(), filePath);
    return props.stringPropertyNames().stream().map(SecretRef::new).toList();
  }

  private Properties loadProperties() {
    final var props = new Properties();
    try (final var reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
      props.load(reader);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return props;
  }
}
