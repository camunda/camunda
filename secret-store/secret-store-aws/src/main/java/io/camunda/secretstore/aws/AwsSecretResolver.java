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
}
