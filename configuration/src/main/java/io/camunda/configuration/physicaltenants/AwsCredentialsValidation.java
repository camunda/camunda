/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.Aws;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.UnifiedConfigurationException;
import org.jspecify.annotations.NullMarked;

/**
 * Per-tenant rule: the resolved {@code aws} section must describe exactly one credential source.
 *
 * <p>Static credentials require both {@code access-key} and {@code secret-key}; web identity
 * requires both {@code role-arn} and {@code web-identity-token-file}; the two modes are mutually
 * exclusive. An entirely empty section is valid and falls back to the AWS SDK default provider
 * chain. Failing fast here surfaces a half-configured identity at boot instead of as an
 * authentication error at request time. Tenants may share credentials — each tenant is validated in
 * isolation.
 */
@NullMarked
final class AwsCredentialsValidation {

  private AwsCredentialsValidation() {}

  static void validate(final String tenantId, final Camunda camunda) {
    final Aws aws = camunda.getAws();
    if (aws.getAccessKey() != null ^ aws.getSecretKey() != null) {
      throw new UnifiedConfigurationException(
          String.format(
              "AWS static credentials of tenant '%s' are incomplete: both 'aws.access-key' "
                  + "and 'aws.secret-key' must be set",
              tenantId));
    }
    if (aws.getRoleArn() != null ^ aws.getWebIdentityTokenFile() != null) {
      throw new UnifiedConfigurationException(
          String.format(
              "AWS web identity of tenant '%s' is incomplete: both 'aws.role-arn' and "
                  + "'aws.web-identity-token-file' must be set",
              tenantId));
    }
    if (aws.hasStaticCredentials() && aws.hasWebIdentity()) {
      throw new UnifiedConfigurationException(
          String.format(
              "AWS configuration of tenant '%s' declares both static credentials and web "
                  + "identity; set only one of them",
              tenantId));
    }
  }
}
