/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.Secrets;
import io.camunda.configuration.Secrets.AwsSecretsManagerStore;
import io.camunda.configuration.Secrets.FileStore;
import io.camunda.configuration.physicaltenants.MapOverlaySpec.MapDescriptor;
import java.util.List;
import org.jspecify.annotations.NullMarked;

/**
 * Per-tenant {@link Secrets} resolution: registers the {@code stores.file} and {@code
 * stores.aws-secrets-manager} named maps so that {@link PhysicalTenantMapOverlay} deep-merges them
 * per physical tenant (the generic two-bind has a defect that drops unmentioned root entries when a
 * tenant overrides only some map keys).
 */
@NullMarked
final class PhysicalTenantSecretConfigurations {

  static final MapOverlaySpec<Secrets> SPEC =
      new MapOverlaySpec<>(
          "secrets",
          Secrets.class,
          Secrets::new,
          List.of(
              new MapDescriptor<>("stores.file", FileStore.class, s -> s.getStores().getFile()),
              new MapDescriptor<>(
                  "stores.aws-secrets-manager",
                  AwsSecretsManagerStore.class,
                  s -> s.getStores().getAwsSecretsManager())),
          MapOverlaySpec.noHook(),
          Camunda::setSecrets);

  private PhysicalTenantSecretConfigurations() {}
}
