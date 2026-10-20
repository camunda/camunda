/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.store;

import java.net.URI;
import org.jspecify.annotations.Nullable;

/**
 * Where an {@link S3Store} keeps its objects, and how it gets there. What is left out comes from
 * the environment, as for the S3 backup store: the region and the credentials from the AWS SDK's
 * default chains, and the endpoint from the region.
 *
 * @param bucket the bucket
 * @param basePath a path in the bucket to keep all keys below, without slashes at the ends
 * @param region the AWS region
 * @param endpoint the endpoint of an S3-compatible store, such as MinIO
 * @param accessKeyId with the secret access key, static credentials
 * @param secretAccessKey with the access key id, static credentials
 * @param forcePathStyle to address the bucket in the path, as MinIO and LocalStack need
 */
public record S3StoreConfig(
    String bucket,
    @Nullable String basePath,
    @Nullable String region,
    @Nullable URI endpoint,
    @Nullable String accessKeyId,
    @Nullable String secretAccessKey,
    boolean forcePathStyle) {

  public S3StoreConfig {
    if (bucket.isBlank()) {
      throw new InvalidStoreConfigException("bucket", "to be a name", "'" + bucket + "'");
    }
    if ((accessKeyId == null) != (secretAccessKey == null)) {
      throw new InvalidStoreConfigException(
          "accessKeyId and secretAccessKey", "to be both set or both unset", "only one");
    }
  }

  /** What {@link #basePath} adds in front of every key: empty, or the path and a slash. */
  String keyPrefix() {
    if (basePath == null) {
      return "";
    }
    var path = basePath;
    while (path.startsWith("/")) {
      path = path.substring(1);
    }
    while (path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }
    return path.isEmpty() ? "" : path + "/";
  }

  /** Like the record's, but without the secret access key. */
  @Override
  public String toString() {
    return "S3StoreConfig[bucket=%s, basePath=%s, region=%s, endpoint=%s, accessKeyId=%s, secretAccessKey=%s, forcePathStyle=%s]"
        .formatted(
            bucket,
            basePath,
            region,
            endpoint,
            accessKeyId,
            secretAccessKey == null ? null : "***",
            forcePathStyle);
  }
}
