/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.validation.AuthorizationValidator;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.security.validation.RoleValidator;
import io.camunda.security.validation.TenantValidator;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.identity.initialize.AuthorizationConfigurer;
import io.camunda.zeebe.engine.processing.identity.initialize.IdentitySetupInitializer;
import io.camunda.zeebe.engine.processing.identity.initialize.RoleConfigurer;
import io.camunda.zeebe.engine.processing.identity.initialize.TenantConfigurer;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IdentitySetupIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public final class IdentitySetupProcessors {
  public static void addIdentitySetupProcessors(
      final KeyGenerator keyGenerator,
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers,
      final SecurityConfiguration securityConfig,
      final EngineConfiguration config) {
    final IdentifierValidator identifierValidator =
        new IdentifierValidator(
            securityConfig.getCompiledIdValidationPattern(),
            securityConfig.getCompiledGroupIdValidationPattern());
    typedRecordProcessors
        .onCommand(
            ValueType.IDENTITY_SETUP,
            IdentitySetupIntent.INITIALIZE,
            new IdentitySetupInitializeProcessor(writers, keyGenerator))
        .withListener(
            new IdentitySetupInitializer(
                securityConfig,
                config.isEnableIdentitySetup(),
                new AuthorizationConfigurer(new AuthorizationValidator(identifierValidator)),
                new TenantConfigurer(new TenantValidator(identifierValidator)),
                new RoleConfigurer(new RoleValidator(identifierValidator))));
  }
}
