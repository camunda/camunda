/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store;

import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * How configured store locations become the values a store addresses. Every caller derives them
 * here so that a location can never be computed two ways — configuration validation compares what
 * the stores will actually use.
 */
@NullMarked
public final class DocumentStorePaths {

  private DocumentStorePaths() {}

  /**
   * The key prefix an object store prepends to a document id: unset becomes empty, and a non-empty
   * prefix always ends in {@code /} so its keys group under one folder instead of beside it.
   */
  public static String keyPrefix(final @Nullable String configuredPath) {
    final String path = Objects.requireNonNullElse(configuredPath, "");
    return path.isEmpty() || path.endsWith("/") ? path : path + "/";
  }

  /** The directory a local store writes into. Trailing separators are not part of the identity. */
  public static Path storageDirectory(final String configuredPath) {
    return Path.of(configuredPath);
  }
}
