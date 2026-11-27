/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.aws;

import static io.camunda.secretstore.SecretErrorCode.INVALID_REF;
import static io.camunda.secretstore.aws.AwsSecretsManagerErrors.classify;
import static io.camunda.secretstore.aws.AwsSecretsManagerErrors.messageFor;
import static io.camunda.secretstore.aws.AwsSecretsManagerErrors.storeUnavailable;

import io.camunda.secretstore.JsonContainerSecret;
import io.camunda.secretstore.SecretResolutionResult;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

/**
 * JSON-container resolution mode: every reference is a key inside one shared AWS secret (a JSON
 * object of key-value pairs), fetched once per {@link #resolve}/{@link #list} call instead of one
 * AWS secret per reference.
 *
 * <p>Expected format: the container secret's string value must be a flat JSON <b>object</b> whose
 * top-level values are all JSON strings, e.g. {@code {"db-password": "s3cr3t", "api-token":
 * "tok3n"}}. Nested objects/arrays are not traversed. Deviations are handled as follows:
 *
 * <ul>
 *   <li>Not valid JSON at all &mdash; {@link #resolve} fails every requested reference with {@link
 *       io.camunda.secretstore.SecretErrorCode#INVALID_REF}; {@link #list} throws {@link
 *       io.camunda.secretstore.SecretStoreUnavailableException}.
 *   <li>Valid JSON but not an object (e.g. an array or a bare string/number) &mdash; same as above;
 *       the container secret is unusable regardless of which method is called.
 *   <li>The requested key is absent, or present with a JSON {@code null} value &mdash; {@link
 *       #resolve} fails only that reference with {@link
 *       io.camunda.secretstore.SecretErrorCode#NOT_FOUND}; {@link #list} is unaffected, since a
 *       missing/null key simply isn't in the field names it returns.
 *   <li>A valid object whose value for a requested key is present but not a JSON string (number,
 *       object, array) &mdash; {@link #resolve} fails only that reference with {@link
 *       io.camunda.secretstore.SecretErrorCode#INVALID_REF}; {@link #list} is unaffected, since it
 *       only inspects field names, not values.
 * </ul>
 */
final class ContainerSecretResolver implements AwsSecretResolver {

  private static final Logger LOG = LoggerFactory.getLogger(ContainerSecretResolver.class);

  private final SecretsManagerClient client;
  private final String containerId;

  ContainerSecretResolver(
      final SecretsManagerClient client, final String pathPrefix, final String containerSecretId) {
    this.client = client;
    containerId = pathPrefix + containerSecretId;
  }

  @Override
  public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
    if (names.isEmpty()) {
      return Map.of();
    }
    LOG.debug(
        "Resolving {} secret refs from AWS Secrets Manager container '{}'",
        names.size(),
        containerId);
    final String raw;
    try {
      raw = fetchRawSecret();
    } catch (final SecretsManagerException e) {
      final var code = classify(e);
      return JsonContainerSecret.failAll(
          names, code, messageFor(code, "container secret", containerId));
    } catch (final SdkClientException e) {
      throw storeUnavailable(e);
    }
    if (raw == null) {
      return JsonContainerSecret.failAll(
          names,
          INVALID_REF,
          "Container secret '" + containerId + "' has no string value (binary secret)");
    }
    return JsonContainerSecret.resolve(containerId, raw, names);
  }

  @Override
  public List<String> list() {
    LOG.debug("Listing keys from AWS Secrets Manager container secret '{}'", containerId);
    final String raw;
    try {
      raw = fetchRawSecret();
    } catch (final SecretsManagerException | SdkClientException e) {
      throw storeUnavailable(e);
    }
    if (raw == null) {
      return List.of();
    }
    return JsonContainerSecret.fieldNames(containerId, raw);
  }

  @Override
  public void validateConnectivity() {
    // container mode reads exactly one secret at runtime, so GetSecretValue on it is both the
    // minimal probe and the one that matches this mode's IAM footprint.
    client.getSecretValue(GetSecretValueRequest.builder().secretId(containerId).build());
  }

  /**
   * Raw {@code secretString} of the container secret. AWS returns exactly one of {@code
   * secretString}/{@code secretBinary} per secret; {@code null} here means the value lives in
   * {@code secretBinary} instead, which this store doesn't support resolving.
   */
  private @Nullable String fetchRawSecret() {
    final var response =
        client.getSecretValue(GetSecretValueRequest.builder().secretId(containerId).build());
    return response.secretString();
  }
}
