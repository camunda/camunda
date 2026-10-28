/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.Secrets.CloudInternalConfig;
import io.camunda.configuration.Secrets.FileStore;
import io.camunda.configuration.Secrets.Stores;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Stops startup when jobs could read the internal-config store of a physical tenant: both stores
 * are built from the same store entry and differ only in scope, so an internal-config scope that
 * lies within the job scope would hand infrastructure secrets to any process model.
 *
 * <p>A reference name is a single token of {@link #NAME}, so a job reaches a secret id only if it
 * is the job prefix followed by such a token, or a key of the job container secret.
 */
final class InternalConfigScopes {

  private static final Pattern NAME = Pattern.compile("[\\p{Alnum}_-]+");

  private InternalConfigScopes() {}

  static void requireSeparateScopes(final String tenantId, final Stores stores) {
    final List<String> clashes = new ArrayList<>();
    stores
        .getFile()
        .forEach((id, store) -> fileClash(storesProperty(tenantId, "file", id), store, clashes));
    stores
        .getAws()
        .forEach(
            (id, store) ->
                cloudClash(
                    storesProperty(tenantId, "aws", id),
                    store.getPathPrefix(),
                    store.getContainerSecretId(),
                    store.getInternalConfig(),
                    clashes));
    stores
        .getGcp()
        .forEach(
            (id, store) ->
                cloudClash(
                    storesProperty(tenantId, "gcp", id),
                    store.getPathPrefix(),
                    store.getContainerSecretId(),
                    store.getInternalConfig(),
                    clashes));
    if (!clashes.isEmpty()) {
      throw new IllegalStateException(
          "Physical tenant '%s' gives its internal-config secret store a scope that jobs can read: %s. Give the internal-config store a scope outside the job scope."
              .formatted(tenantId, String.join("; ", clashes)));
    }
  }

  private static void fileClash(
      final String property, final FileStore store, final List<String> clashes) {
    final var internalConfig = store.getInternalConfig();
    if (internalConfig == null) {
      return;
    }
    if (internalConfig.getPath().isBlank()) {
      clashes.add("%s.internal-config.path is not set".formatted(property));
      return;
    }
    final var jobs = Path.of(store.getPath()).toAbsolutePath().normalize();
    final var internal = Path.of(internalConfig.getPath()).toAbsolutePath().normalize();
    if (internal.startsWith(jobs)) {
      clashes.add(
          "%s.internal-config.path ('%s') is within %s.path ('%s')"
              .formatted(property, internalConfig.getPath(), property, store.getPath()));
    }
  }

  private static void cloudClash(
      final String property,
      final @Nullable String jobPrefix,
      final @Nullable String jobContainer,
      final @Nullable CloudInternalConfig internalConfig,
      final List<String> clashes) {
    if (internalConfig == null) {
      return;
    }
    final var job = new CloudScope(property, "", jobPrefix, jobContainer);
    final var internal =
        new CloudScope(
            property,
            "internal-config.",
            internalConfig.getPathPrefix(),
            internalConfig.getContainerSecretId());
    if (job.reaches(internal)) {
      clashes.add("%s is within %s".formatted(internal.describe(), job.describe()));
    }
  }

  private static String storesProperty(
      final String tenantId, final String storeType, final String storeId) {
    return (PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID.equals(tenantId)
            ? "camunda.secrets.stores."
            : "camunda.physical-tenants." + tenantId + ".secrets.stores.")
        + storeType
        + "."
        + storeId;
  }

  private record CloudScope(
      String property,
      String block,
      @Nullable String pathPrefix,
      @Nullable String containerSecretId) {

    private String prefix() {
      return pathPrefix == null || pathPrefix.isBlank() ? "" : pathPrefix;
    }

    private @Nullable String containerId() {
      return containerSecretId == null ? null : prefix() + containerSecretId;
    }

    private boolean reaches(final CloudScope other) {
      final var container = containerId();
      final var otherContainer = other.containerId();
      if (container != null) {
        return otherContainer != null
            ? container.equals(otherContainer)
            : isName(other.prefix(), container, true);
      }
      return otherContainer != null
          ? isName(prefix(), otherContainer, true)
          : isName(prefix(), other.prefix(), false);
    }

    private static boolean isName(
        final String prefix, final String secretId, final boolean nonEmpty) {
      if (!secretId.startsWith(prefix)) {
        return false;
      }
      final var rest = secretId.substring(prefix.length());
      return rest.isEmpty() ? !nonEmpty : NAME.matcher(rest).matches();
    }

    private String describe() {
      final var parts = new ArrayList<String>();
      parts.add("%s.%spath-prefix ('%s')".formatted(property, block, prefix()));
      if (containerSecretId != null) {
        parts.add("%s.%scontainer-secret-id ('%s')".formatted(property, block, containerSecretId));
      }
      return String.join(" with ", parts);
    }
  }
}
