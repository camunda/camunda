/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.Document.AwsStore;
import io.camunda.configuration.Document.AzureStore;
import io.camunda.configuration.Document.GcpStore;
import io.camunda.configuration.Document.LocalStore;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The minimal, location-determining tuple that decides whether two physical tenants would write
 * into the <em>same</em> physical document-store location: {@code (provider, coordinates)}. Two
 * stores resolving to equal {@code DocumentStoreLocation} values collide.
 *
 * <p>Mirrors {@link StorageIdentity}'s philosophy: identity is location-only and minimal — adding a
 * non-location field (e.g. AWS {@code region}, since S3 bucket names are global) could let an
 * incidental difference mask a real collision. Detection is therefore best-effort and static.
 *
 * <p><b>SPIKE note:</b> the production target is a {@code sealed interface} implemented by the
 * store POJOs, so adding a provider is a compile-time obligation (exhaustive {@code switch}). This
 * POC uses per-provider factory methods instead, to avoid touching the shared {@code Document}
 * POJOs; the per-provider {@code coordinates} are identical to what the sealed version would yield.
 *
 * <table>
 *   <caption>Per-provider location coordinates</caption>
 *   <tr><th>Provider</th><th>Coordinates</th><th>Notes</th></tr>
 *   <tr><td>{@code aws}</td><td>bucketName + bucketPath</td>
 *       <td>region excluded (S3 names global); bucketTtl is not location</td></tr>
 *   <tr><td>{@code gcp}</td><td>bucketName + prefix</td><td></td></tr>
 *   <tr><td>{@code azure}</td><td>containerName + containerPath + endpoint</td>
 *       <td>account/credential discrimination deferred (#54366)</td></tr>
 *   <tr><td>{@code local}</td><td>path</td><td>test-only</td></tr>
 *   <tr><td>{@code in-memory}</td><td>none → never collides</td>
 *       <td>test-only; ephemeral per-instance</td></tr>
 * </table>
 *
 * @param provider the provider discriminator ({@code aws}/{@code gcp}/{@code azure}/{@code local})
 * @param coordinates the normalized location coordinates for that provider
 */
@NullMarked
record DocumentStoreLocation(String provider, List<String> coordinates) {

  static DocumentStoreLocation aws(final AwsStore store) {
    // region excluded: S3 bucket names are globally unique, so it is redundant and could mask a
    // collision. bucketTtl is a retention policy, not a location.
    return new DocumentStoreLocation(
        "aws", List.of(norm(store.getBucketName()), norm(store.getBucketPath())));
  }

  static DocumentStoreLocation gcp(final GcpStore store) {
    return new DocumentStoreLocation(
        "gcp", List.of(norm(store.getBucketName()), norm(store.getPrefix())));
  }

  static DocumentStoreLocation azure(final AzureStore store) {
    // connectionString (credential) excluded; account discrimination deferred (#54366).
    return new DocumentStoreLocation(
        "azure",
        List.of(
            norm(store.getContainerName()),
            norm(store.getContainerPath()),
            norm(store.getEndpoint())));
  }

  static DocumentStoreLocation local(final LocalStore store) {
    return new DocumentStoreLocation("local", List.of(norm(store.getPath())));
  }

  // in-memory has no location() — an ephemeral per-instance object that can never collide. There is
  // intentionally no factory: callers skip the in-memory map entirely.

  /**
   * Light, best-effort normalization mirroring {@link StorageIdentity}: trim, strip a trailing
   * slash, lowercase; {@code null} maps to empty so omitted ≡ "" and both collide.
   */
  private static String norm(final @Nullable String value) {
    if (value == null) {
      return "";
    }
    String normalized = value.trim();
    if (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized.toLowerCase();
  }

  /** A human-readable rendering of this location for error messages. */
  String describe() {
    return String.format("provider=%s, coordinates=%s", provider, coordinates);
  }
}
