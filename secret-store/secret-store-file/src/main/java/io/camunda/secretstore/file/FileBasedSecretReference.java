/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.file;

import io.camunda.secretstore.SecretReference;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A secret reference resolved by {@link FileBasedSecretStore}, where {@code name} is a file name
 * within the configured secrets directory.
 *
 * <p>Names containing a path separator ({@code /} or {@code \}) or equal to a traversal token
 * ({@code .} or {@code ..}) are rejected, so the name alone cannot escape the directory. This
 * constrains the <em>name</em> only: a symlink inside the directory (as used by the Kubernetes
 * {@code ..data} layout) is still followed when the value is read.
 */
public record FileBasedSecretReference(String name) implements SecretReference {
  public FileBasedSecretReference {
    Objects.requireNonNull(name, "name must not be null");
    if (name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
      throw new IllegalArgumentException("name must not contain a path separator: " + name);
    }
    if (".".equals(name) || "..".equals(name)) {
      throw new IllegalArgumentException("name must not be a directory traversal token: " + name);
    }
  }

  /** Returns whether {@code name} is a legal, single-segment secret name. */
  static boolean isValid(final @Nullable String name) {
    if (name == null) {
      return false;
    }
    try {
      new FileBasedSecretReference(name);
      return true;
    } catch (final IllegalArgumentException e) {
      return false;
    }
  }
}
