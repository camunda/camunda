/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.gcp;

import static io.camunda.secretstore.gcp.GcpSecretManagerErrors.classify;
import static io.camunda.secretstore.gcp.GcpSecretManagerErrors.messageFor;
import static io.camunda.secretstore.gcp.GcpSecretManagerErrors.storeUnavailable;

import com.google.api.gax.rpc.ApiException;
import com.google.cloud.secretmanager.v1.ListSecretsRequest;
import com.google.cloud.secretmanager.v1.ProjectName;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretName;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default resolution mode: each reference name maps 1:1 to a GCP secret id ({@code pathPrefix +
 * name}). Issues one {@code accessSecretVersion} call per reference against the {@code latest}
 * version.
 */
final class OneByOneSecretResolver implements GcpSecretResolver {

  private static final Logger LOG = LoggerFactory.getLogger(OneByOneSecretResolver.class);
  private static final String LATEST_VERSION = "latest";

  private final SecretManagerServiceClient client;
  private final @Nullable String projectId;
  private final String pathPrefix;

  OneByOneSecretResolver(
      final SecretManagerServiceClient client,
      final @Nullable String projectId,
      final String pathPrefix) {
    this.client = client;
    this.projectId = projectId;
    this.pathPrefix = pathPrefix;
  }

  @Override
  public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
    if (names.isEmpty()) {
      return Map.of();
    }
    LOG.debug("Resolving {} secret refs from GCP Secret Manager", names.size());
    final Map<String, SecretResolutionResult> results = new LinkedHashMap<>(names.size());
    for (final var name : names) {
      results.put(name, resolveSingle(name));
    }
    return results;
  }

  @Override
  public List<String> list() {
    LOG.debug(
        "Listing secrets from GCP Secret Manager project '{}' with prefix '{}'",
        projectId,
        pathPrefix);
    final var names = new ArrayList<String>();
    try {
      for (final var secret : client.listSecrets(ProjectName.of(requireProjectId())).iterateAll()) {
        final var parsedName =
            Objects.requireNonNull(
                SecretName.parse(secret.getName()),
                "GCP returned an unparsable secret name: " + secret.getName());
        final var secretId = parsedName.getSecret();
        if (secretId.startsWith(pathPrefix)) {
          final var logicalName = secretId.substring(pathPrefix.length());
          if (!logicalName.isBlank()) {
            names.add(logicalName);
          }
        }
      }
      return names;
    } catch (final RuntimeException e) {
      throw storeUnavailable(e);
    }
  }

  @Override
  public boolean resolvesOneByOne() {
    return true;
  }

  @Override
  public void validateConnectivity() {
    // one-by-one mode resolves via accessSecretVersion and lists via listSecrets; a single-result
    // listSecrets is the cheapest probe that exercises this mode's IAM footprint.
    final var projectId = requireProjectId();
    client.listSecrets(
        ListSecretsRequest.newBuilder()
            .setParent(ProjectName.of(projectId).toString())
            .setPageSize(1)
            .build());
  }

  private SecretResolutionResult resolveSingle(final String name) {
    final var secretId = secretId(name);
    try {
      final var projectId = requireProjectId();
      final var response =
          client.accessSecretVersion(SecretVersionName.of(projectId, secretId, LATEST_VERSION));
      return new Resolved(response.getPayload().getData().toStringUtf8());
    } catch (final ApiException e) {
      final var code = classify(e);
      return new Failed(code, messageFor(code, "secret", secretId), e);
    } catch (final RuntimeException e) {
      throw storeUnavailable(e);
    }
  }

  private String secretId(final String name) {
    return pathPrefix + name;
  }

  private String requireProjectId() {
    return Objects.requireNonNull(projectId, "projectId must be non-null");
  }
}
