/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.aws;

import java.net.URI;
import java.util.concurrent.ExecutorService;

public class AwsDocumentStoreFactory {

  public static AwsDocumentStore create(
      final String bucketName,
      final Long defaultTTL,
      final String bucketPath,
      final ExecutorService executor) {
    return create(bucketName, defaultTTL, bucketPath, executor, AwsClientOptions.sdkDefaults());
  }

  /**
   * @deprecated use the {@link AwsClientOptions} overload; kept only so callers compiled against an
   *     earlier release still link.
   */
  @Deprecated(forRemoval = true)
  public static AwsDocumentStore create(
      final String bucketName,
      final Long defaultTTL,
      final String bucketPath,
      final ExecutorService executor,
      final URI endpointOverride,
      final Boolean forcePathStyle,
      final Boolean chunkedEncodingEnabled) {
    return create(
        bucketName,
        defaultTTL,
        bucketPath,
        executor,
        endpointOverride,
        forcePathStyle,
        chunkedEncodingEnabled,
        null);
  }

  /**
   * @deprecated use the {@link AwsClientOptions} overload; kept only so callers compiled against an
   *     earlier release still link.
   */
  @Deprecated(forRemoval = true)
  public static AwsDocumentStore create(
      final String bucketName,
      final Long defaultTTL,
      final String bucketPath,
      final ExecutorService executor,
      final URI endpointOverride,
      final Boolean forcePathStyle,
      final Boolean chunkedEncodingEnabled,
      final Boolean supportLegacyMd5) {
    return create(
        bucketName,
        defaultTTL,
        bucketPath,
        executor,
        new AwsClientOptions(
            endpointOverride,
            forcePathStyle,
            chunkedEncodingEnabled,
            supportLegacyMd5,
            null,
            null,
            null));
  }

  public static AwsDocumentStore create(
      final String bucketName,
      final Long defaultTTL,
      final String bucketPath,
      final ExecutorService executor,
      final AwsClientOptions clientOptions) {
    return new AwsDocumentStore(bucketName, defaultTTL, bucketPath, executor, clientOptions);
  }
}
