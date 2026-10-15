/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.hashicorpvault;

import java.net.URI;
import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * Configuration for a {@link HashiCorpVaultSecretStore}.
 *
 * <p>Credentials that grant access (the AppRole {@code secret_id}, a static token) are never part
 * of this config: only the <em>location</em> they are read from (a file path or an environment
 * variable name) is. They are re-read on every sign-in, so a rotated file is picked up without a
 * restart.
 *
 * @param address base URL of the HashiCorp Vault server, e.g. {@code https://vault.example:8200}.
 *     Must be {@code https} unless {@code allowInsecure} is set
 * @param namespace optional HashiCorp Vault Enterprise / OpenBao namespace, sent as the {@code
 *     X-Vault-Namespace} header on every call, sign-in included
 * @param kvVersion the KV secrets engine version mounted at {@code mount}, {@code 1} or {@code 2}
 * @param mount the KV mount path, e.g. {@code secret} or {@code kv/team-a}
 * @param pathPrefix optional path inside the mount under which every secret entry lives, e.g.
 *     {@code camunda/}; a reference {@code db-password} then reads {@code
 *     <mount>/camunda/db-password}
 * @param containerPath opt-in: instead of one entry per reference, read this one entry (relative to
 *     {@code pathPrefix}) and treat each of its fields as a secret named after the field
 * @param field the field read from each entry in per-entry mode
 * @param authMethod how the store signs in
 * @param authMount the mount path of the auth method; defaults to the method's conventional path
 * @param role the AppRole role id ({@code role_id}, not a credential), or the Kubernetes role name
 * @param secretIdFile AppRole: file containing the {@code secret_id}
 * @param secretIdEnv AppRole: environment variable containing the {@code secret_id}
 * @param jwtPath Kubernetes: file containing the service account JWT
 * @param tokenFile token auth (testing only): file containing the token
 * @param tokenEnv token auth (testing only): environment variable containing the token
 * @param caBundlePath optional PEM file with the CA certificate(s) to trust for {@code address}
 * @param allowInsecure allow a plain {@code http} address, for local development only
 * @param callTimeout upper bound on one HTTP call to HashiCorp Vault
 */
public record HashiCorpVaultStoreConfig(
    URI address,
    @Nullable String namespace,
    int kvVersion,
    String mount,
    @Nullable String pathPrefix,
    @Nullable String containerPath,
    String field,
    AuthMethod authMethod,
    String authMount,
    @Nullable String role,
    @Nullable String secretIdFile,
    @Nullable String secretIdEnv,
    String jwtPath,
    @Nullable String tokenFile,
    @Nullable String tokenEnv,
    @Nullable String caBundlePath,
    boolean allowInsecure,
    Duration callTimeout) {

  public static final int DEFAULT_KV_VERSION = 2;
  public static final String DEFAULT_MOUNT = "secret";
  public static final String DEFAULT_FIELD = "value";
  public static final String DEFAULT_KUBERNETES_JWT_PATH =
      "/var/run/secrets/kubernetes.io/serviceaccount/token";

  /**
   * Same bound as the AWS and GCP stores: the background resolution calls this store from an
   * IO-bound actor thread shared with every partition's exporter (camunda/camunda#62869).
   */
  public static final Duration DEFAULT_CALL_TIMEOUT = Duration.ofSeconds(5);

  public HashiCorpVaultStoreConfig {
    if (address == null || address.getScheme() == null || address.getHost() == null) {
      throw new IllegalArgumentException("address must be an absolute URL, but was " + address);
    }
    if (!"https".equalsIgnoreCase(address.getScheme())
        && !("http".equalsIgnoreCase(address.getScheme()) && allowInsecure)) {
      throw new IllegalArgumentException(
          "address must use https (or http with allow-insecure enabled), but was " + address);
    }
    if (namespace != null && namespace.isBlank()) {
      throw new IllegalArgumentException("namespace must not be blank");
    }
    if (kvVersion != 1 && kvVersion != 2) {
      throw new IllegalArgumentException("kvVersion must be 1 or 2, but was " + kvVersion);
    }
    requirePath("mount", mount, false);
    if (pathPrefix != null) {
      requirePath("pathPrefix", pathPrefix, true);
    }
    if (containerPath != null) {
      requirePath("containerPath", containerPath, false);
    }
    if (field == null || field.isBlank()) {
      throw new IllegalArgumentException("field must not be blank");
    }
    if (authMethod == null) {
      throw new IllegalArgumentException("authMethod must be set");
    }
    requirePath("authMount", authMount, false);
    switch (authMethod) {
      case APPROLE -> {
        requireNonBlank("role (the AppRole role_id)", role);
        requireExactlyOne("secretIdFile", secretIdFile, "secretIdEnv", secretIdEnv);
      }
      case KUBERNETES -> {
        requireNonBlank("role", role);
        requireNonBlank("jwtPath", jwtPath);
      }
      case TOKEN -> requireExactlyOne("tokenFile", tokenFile, "tokenEnv", tokenEnv);
      default -> throw new IllegalStateException("Unexpected auth method " + authMethod);
    }
    if (callTimeout == null || !callTimeout.isPositive()) {
      throw new IllegalArgumentException("callTimeout must be positive, but was " + callTimeout);
    }
  }

  /** The KV path of the entry holding {@code name}, relative to the mount. */
  String entryPath(final String name) {
    return (pathPrefix == null ? "" : pathPrefix) + name;
  }

  private static void requirePath(
      final String property, final @Nullable String path, final boolean allowTrailingSlash) {
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException(property + " must not be blank");
    }
    if (path.startsWith("/")
        || (!allowTrailingSlash && path.endsWith("/"))
        || path.contains("//")
        || path.contains("?")
        || path.contains("#")) {
      throw new IllegalArgumentException(
          property + " must be a relative path without empty segments, but was '" + path + "'");
    }
    for (final var segment : path.split("/")) {
      if (segment.equals("..") || segment.equals(".")) {
        throw new IllegalArgumentException(
            property + " must not contain '.' or '..' segments, but was '" + path + "'");
      }
    }
  }

  private static void requireNonBlank(final String property, final @Nullable String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(property + " must be set for this auth method");
    }
  }

  private static void requireExactlyOne(
      final String first,
      final @Nullable String firstValue,
      final String second,
      final @Nullable String secondValue) {
    final boolean hasFirst = firstValue != null && !firstValue.isBlank();
    final boolean hasSecond = secondValue != null && !secondValue.isBlank();
    if (hasFirst == hasSecond) {
      throw new IllegalArgumentException(
          "exactly one of " + first + " or " + second + " must be set for this auth method");
    }
  }

  public enum AuthMethod {
    APPROLE("approle"),
    KUBERNETES("kubernetes"),
    TOKEN("token");

    private final String defaultMount;

    AuthMethod(final String defaultMount) {
      this.defaultMount = defaultMount;
    }

    public String defaultMount() {
      return defaultMount;
    }
  }
}
