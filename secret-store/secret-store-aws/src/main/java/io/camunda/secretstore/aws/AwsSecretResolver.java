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
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * One AWS Secrets Manager resolution algorithm: how references are mapped to secret ids and read.
 * {@link AwsSecretsManagerSecretStore} picks exactly one implementation at construction time based
 * on configuration and delegates every call to it.
 */
interface AwsSecretResolver {

  /**
   * @throws SecretStoreUnavailableException if the backing store cannot be accessed
   */
  Map<AwsSecretsManagerSecretReference, SecretResolutionResult> resolve(
      Set<AwsSecretsManagerSecretReference> refs);

  /**
   * @throws SecretStoreUnavailableException if the backing store cannot be accessed or its content
   *     is malformed
   */
  Collection<AwsSecretsManagerSecretReference> list();
}
