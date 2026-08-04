/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.aws;

import java.net.URI;

/**
 * Per-store overrides applied to the S3 client and presigner of a single AWS document store. Every
 * component is optional: a {@code null} means "leave it to the AWS SDK", which resolves it from the
 * process environment.
 */
public record AwsClientOptions(
    URI endpointOverride,
    Boolean forcePathStyle,
    Boolean chunkedEncodingEnabled,
    Boolean supportLegacyMd5,
    String region) {

  public static AwsClientOptions sdkDefaults() {
    return new AwsClientOptions(null, null, null, null, null);
  }
}
