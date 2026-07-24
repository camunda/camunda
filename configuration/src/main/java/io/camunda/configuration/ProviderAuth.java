/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Cloud provider authentication ({@code camunda.provider-auth.*}): identities Camunda uses to
 * authenticate against cloud provider services (e.g. AWS OpenSearch, Aurora, S3), grouped here so
 * they can be shared across consumers and, in the future, extended to other providers.
 */
public class ProviderAuth {

  @NestedConfigurationProperty private Aws aws = new Aws();

  public Aws getAws() {
    return aws;
  }

  public void setAws(final Aws aws) {
    this.aws = aws;
  }
}
