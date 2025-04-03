/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.user;

import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.authorization.IdentitySetupRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.intent.IdentitySetupIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import io.camunda.zeebe.util.FeatureFlags;
import org.slf4j.Logger;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

public final class IdentitySetupInitializer implements StreamProcessorLifecycleAware, Task {
  public static final String DEFAULT_ROLE_NAME = "Admin";
  public static final String DEFAULT_TENANT_ID = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
  public static final String DEFAULT_TENANT_NAME = "Default";
  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;
  private final SecurityConfiguration securityConfig;
  private final FeatureFlags featureFlags;
  private final PasswordEncoder passwordEncoder;

  public IdentitySetupInitializer(
      final SecurityConfiguration securityConfig, final FeatureFlags featureFlags) {
    this.securityConfig = securityConfig;
    this.featureFlags = featureFlags;
    passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    // We can disable identity setup by disabling the feature flag. This is useful to prevent
    // interference in our engine tests, as this setup will write "unexpected" commands/events
    if (!featureFlags.enableIdentitySetup()) {
      return;
    }

    if (context.getPartitionId() != Protocol.DEPLOYMENT_PARTITION) {
      // We should only create users on the deployment partition. The command will be distributed to
      // the other partitions using our command distribution mechanism.
      LOG.debug(
          "Skipping identity setup on partition {} as it is not the deployment partition",
          context.getPartitionId());
      return;
    }

    // We use a timestamp of 0L to ensure this is runs immediately once the stream processor is
    // started,
    context.getScheduleService().runAtAsync(0L, this);
  }

  @Override
  public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    final var setupRecord = new IdentitySetupRecord();

    final var defaultRole = new RoleRecord().setName(DEFAULT_ROLE_NAME);
    setupRecord.setDefaultRole(defaultRole);

    securityConfig
        .getInitialization()
        .getUsers()
        .forEach(
            user -> {
              final var userRecord =
                  new UserRecord()
                      .setUsername(user.getUsername())
                      .setName(user.getName())
                      .setEmail(user.getEmail())
                      .setPassword(passwordEncoder.encode(user.getPassword()));
              setupRecord.addUser(userRecord);
            });

    final var defaultTenant =
        new TenantRecord().setTenantId(DEFAULT_TENANT_ID).setName(DEFAULT_TENANT_NAME);
    setupRecord.setDefaultTenant(defaultTenant);

    securityConfig
        .getInitialization()
        .getMappings()
        .forEach(
            mapping -> {
              final var mappingrecord =
                  new MappingRecord()
                      .setMappingId(mapping.getMappingId())
                      .setClaimName(mapping.getClaimName())
                      .setClaimValue(mapping.getClaimValue());
              setupRecord.addMapping(mappingrecord);
            });

    taskResultBuilder.appendCommandRecord(IdentitySetupIntent.INITIALIZE, setupRecord);
    return taskResultBuilder.build();
  }
}
