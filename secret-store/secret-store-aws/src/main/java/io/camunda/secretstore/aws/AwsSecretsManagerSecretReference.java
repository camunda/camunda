/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.aws;

import io.camunda.secretstore.SecretReference;
import java.util.Objects;

/**
 * A reference to a secret stored in AWS Secrets Manager.
 *
 * <p>The {@code name} is the logical secret name as used in the model. The backing AWS secret id is
 * derived by the store as {@code pathPrefix + name}.
 */
public record AwsSecretsManagerSecretReference(String name) implements SecretReference {

  public AwsSecretsManagerSecretReference {
    Objects.requireNonNull(name, "name must not be null");
    if (name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
  }
}
