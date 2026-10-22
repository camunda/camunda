/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.store;

import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * How to open a store, for {@link ObjectStores#open}: its type, and the settings of that type. The
 * others are ignored. For a {@link Type#FILESYSTEM}, the {@code path} to its root is needed. For
 * {@link Type#S3}, the {@code bucket} is, and the rest are those of {@link S3StoreConfig}.
 *
 * @param type {@code filesystem} or {@code s3}
 * @param path the root directory of a filesystem store
 * @param bucket the bucket of an S3 store
 * @param basePath a path in the bucket to keep all keys below
 * @param region the AWS region
 * @param endpoint the endpoint of an S3-compatible store
 * @param accessKeyId with the secret access key, static credentials
 * @param secretAccessKey with the access key id, static credentials
 * @param forcePathStyle to address the bucket in the path, as MinIO and LocalStack need
 */
public record StoreConfig(
    Type type,
    @Nullable Path path,
    @Nullable String bucket,
    @Nullable String basePath,
    @Nullable String region,
    @Nullable URI endpoint,
    @Nullable String accessKeyId,
    @Nullable String secretAccessKey,
    boolean forcePathStyle) {

  public StoreConfig {
    if (type == Type.FILESYSTEM && path == null) {
      throw new InvalidStoreConfigException("path", "to be set for a filesystem store", "none");
    }
    if (type == Type.S3 && (bucket == null || bucket.isBlank())) {
      throw new InvalidStoreConfigException("bucket", "to be set for an S3 store", "none");
    }
  }

  /** A store on the filesystem, with this directory as its root. */
  public static StoreConfig filesystem(final Path path) {
    return new StoreConfig(Type.FILESYSTEM, path, null, null, null, null, null, null, false);
  }

  /** An S3 store with this configuration. */
  public static StoreConfig s3(final S3StoreConfig config) {
    return new StoreConfig(
        Type.S3,
        null,
        config.bucket(),
        config.basePath(),
        config.region(),
        config.endpoint(),
        config.accessKeyId(),
        config.secretAccessKey(),
        config.forcePathStyle());
  }

  /** The S3 configuration in this one. */
  S3StoreConfig toS3() {
    if (bucket == null) {
      throw new InvalidStoreConfigException("bucket", "to be set for an S3 store", "none");
    }
    return new S3StoreConfig(
        bucket, basePath, region, endpoint, accessKeyId, secretAccessKey, forcePathStyle);
  }

  /** Like the record's, but without the secret access key. */
  @Override
  public String toString() {
    return "StoreConfig[type=%s, path=%s, bucket=%s, basePath=%s, region=%s, endpoint=%s, accessKeyId=%s, secretAccessKey=%s, forcePathStyle=%s]"
        .formatted(
            type,
            path,
            bucket,
            basePath,
            region,
            endpoint,
            accessKeyId,
            secretAccessKey == null ? null : "***",
            forcePathStyle);
  }

  /** The kinds of store. */
  public enum Type {
    FILESYSTEM,
    S3;

    /** The type by its name in a configuration, {@code filesystem} or {@code s3}, in any case. */
    public static Type of(final String name) {
      return switch (name.toLowerCase(Locale.ROOT)) {
        case "filesystem" -> FILESYSTEM;
        case "s3" -> S3;
        default -> throw new InvalidStoreConfigException("type", "to be filesystem or s3", name);
      };
    }
  }
}
