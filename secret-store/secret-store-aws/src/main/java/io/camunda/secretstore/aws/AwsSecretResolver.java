/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.aws;

import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretStoreUnavailableException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One AWS Secrets Manager resolution algorithm: how reference names are mapped to secret ids and
 * read. {@link AwsSecretsManagerSecretStore} picks exactly one implementation at construction time
 * based on configuration and delegates every call to it.
 */
interface AwsSecretResolver {

  /**
   * @throws SecretStoreUnavailableException if the backing store cannot be accessed
   */
  Map<String, SecretResolutionResult> resolve(Set<String> names);

  /**
   * @throws SecretStoreUnavailableException if the backing store cannot be accessed or its content
   *     is malformed
   */
  List<String> list();

  /**
   * Probes AWS with the same API this resolver uses at runtime, so a startup connectivity check
   * never demands a broader IAM policy than resolution itself needs. Any AWS/SDK error propagates
   * to the caller, which logs a warning and continues; called once at construction time.
   */
  void validateConnectivity();

  /**
   * How many names one {@link #resolve} call covers. {@code 1} for the unbatched flat mode, issuing
   * one {@code GetSecretValue} call per name; the configured batch size for the batched flat mode,
   * issuing one {@code BatchGetSecretValue} call per that many names; {@link Integer#MAX_VALUE} for
   * a container secret, which reads every requested name from one shared secret in a single call.
   */
  default int namesPerCall() {
    return Integer.MAX_VALUE;
  }
}
