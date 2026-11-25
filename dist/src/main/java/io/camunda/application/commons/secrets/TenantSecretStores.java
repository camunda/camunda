/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import io.camunda.secretstore.SecretStoreRegistry;

/** The secret stores of one physical tenant, and their associated resources. */
record TenantSecretStores(SecretStoreRegistry registry, SecretStoreResources resources)
    implements AutoCloseable {

  /** Makes the meters of the stores visible, once the stores they replace have released theirs. */
  void publishMeters() {
    resources.publishMeters();
  }

  @Override
  public void close() {
    resources.close();
  }
}
