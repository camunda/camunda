/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.store;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.defaultsmode.DefaultsMode;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * An {@link ObjectStore} in an S3 bucket, with the AWS SDK's synchronous client, which also works
 * with S3-compatible stores such as MinIO and LocalStack. All keys sit below the configuration's
 * base path, if it has one. Failures of the SDK are {@link ObjectStoreException}s.
 */
public final class S3Store implements ObjectStore {

  /** What S3 returns at most for one listing request. */
  private static final int MAX_PAGE_SIZE = 1000;

  private static final int INVALID_RANGE = 416;
  private static final int NOT_FOUND = 404;

  private final S3Client client;
  private final String bucket;
  private final String keyPrefix;
  private final int pageSize;
  private final URI root;

  public S3Store(final S3StoreConfig config) {
    this(config, MAX_PAGE_SIZE);
  }

  /** For tests, which page through listings of a few objects. */
  S3Store(final S3StoreConfig config, final int pageSize) {
    this.pageSize = Math.clamp(pageSize, 1, MAX_PAGE_SIZE);
    bucket = config.bucket();
    keyPrefix = config.keyPrefix();
    root = s3Uri(keyPrefix);
    client = buildClient(config);
  }

  @Override
  public URI root() {
    return root;
  }

  @Override
  public void put(final String key, final byte[] content) throws IOException {
    final var objectKey = objectKey(ObjectKeys.requireKey(key));
    call(
        "put",
        key,
        () ->
            client.putObject(
                request -> request.bucket(bucket).key(objectKey), RequestBody.fromBytes(content)));
  }

  @Override
  public Optional<byte[]> get(final String key) throws IOException {
    final var objectKey = objectKey(ObjectKeys.requireKey(key));
    try {
      return Optional.of(
          call(
              "get",
              key,
              () ->
                  client
                      .getObjectAsBytes(request -> request.bucket(bucket).key(objectKey))
                      .asByteArray()));
    } catch (final NoSuchObjectException e) {
      return Optional.empty();
    }
  }

  @Override
  public byte[] getRange(final String key, final long offset, final int length) throws IOException {
    if (offset < 0 || length < 0) {
      throw new IllegalArgumentException(
          "Expected an offset and a length of 0 or more, but got %d and %d"
              .formatted(offset, length));
    }
    if (length == 0) {
      // A range can't be empty, but a missing object is still an error.
      if (head(key).isEmpty()) {
        throw new NoSuchObjectException(key);
      }
      return new byte[0];
    }
    final var objectKey = objectKey(ObjectKeys.requireKey(key));
    final long last = offset > Long.MAX_VALUE - length ? Long.MAX_VALUE : offset + length - 1;
    final var range = "bytes=%d-%d".formatted(offset, last);
    try {
      return call(
          "get a range of",
          key,
          () ->
              client
                  .getObjectAsBytes(request -> request.bucket(bucket).key(objectKey).range(range))
                  .asByteArray());
    } catch (final InvalidRangeException e) {
      return new byte[0];
    }
  }

  @Override
  public Optional<StoredObject> head(final String key) throws IOException {
    final var objectKey = objectKey(ObjectKeys.requireKey(key));
    try {
      return Optional.of(
          call(
              "head",
              key,
              () -> {
                final var response =
                    client.headObject(request -> request.bucket(bucket).key(objectKey));
                return new StoredObject(key, response.contentLength(), response.lastModified());
              }));
    } catch (final NoSuchObjectException e) {
      return Optional.empty();
    }
  }

  @Override
  public List<StoredObject> list(
      final String prefix, @Nullable final String startAfter, final int limit) throws IOException {
    ObjectKeys.requireLimit(limit);
    final var objects = new ArrayList<StoredObject>();
    String continuationToken = null;
    while (objects.size() < limit) {
      final var request =
          listRequest(prefix, Math.min(pageSize, limit - objects.size()), continuationToken);
      if (continuationToken == null && startAfter != null) {
        request.startAfter(objectKey(startAfter));
      }
      final var response = listPage(prefix, request.build());
      for (final var object : response.contents()) {
        objects.add(
            new StoredObject(
                key(object.key()),
                object.size() == null ? 0 : object.size(),
                object.lastModified() == null ? Instant.EPOCH : object.lastModified()));
      }
      if (!Boolean.TRUE.equals(response.isTruncated())) {
        break;
      }
      continuationToken = response.nextContinuationToken();
    }
    return objects;
  }

  @Override
  public List<String> listPrefixes(final String prefix) throws IOException {
    ObjectKeys.requireDirectory(prefix);
    final var prefixes = new ArrayList<String>();
    String continuationToken = null;
    do {
      final var response =
          listPage(prefix, listRequest(prefix, pageSize, continuationToken).delimiter("/").build());
      for (final var commonPrefix : response.commonPrefixes()) {
        prefixes.add(key(commonPrefix.prefix()));
      }
      continuationToken =
          Boolean.TRUE.equals(response.isTruncated()) ? response.nextContinuationToken() : null;
    } while (continuationToken != null);
    prefixes.sort(KeyOrder.COMPARATOR);
    return prefixes;
  }

  @Override
  public void delete(final String key) throws IOException {
    final var objectKey = objectKey(ObjectKeys.requireKey(key));
    call(
        "delete", key, () -> client.deleteObject(request -> request.bucket(bucket).key(objectKey)));
  }

  @Override
  public void close() {
    client.close();
  }

  @Override
  public String toString() {
    return "S3Store[" + root + "]";
  }

  private ListObjectsV2Request.Builder listRequest(
      final String prefix, final int maxKeys, @Nullable final String continuationToken) {
    final var request = ListObjectsV2Request.builder().bucket(bucket).maxKeys(maxKeys);
    if (!keyPrefix.isEmpty() || !prefix.isEmpty()) {
      request.prefix(objectKey(prefix));
    }
    if (continuationToken != null) {
      request.continuationToken(continuationToken);
    }
    return request;
  }

  private ListObjectsV2Response listPage(final String prefix, final ListObjectsV2Request request)
      throws IOException {
    return call("list", prefix, () -> client.listObjectsV2(request));
  }

  private String objectKey(final String key) {
    return keyPrefix + key;
  }

  /** The key in the store of a key in the bucket. */
  private String key(final String objectKey) {
    return objectKey.substring(keyPrefix.length());
  }

  /**
   * Makes the request. A missing object is a {@link NoSuchObjectException}, a range that starts
   * after the end of the object an {@link InvalidRangeException}, and any other failure of the SDK
   * an {@link ObjectStoreException}.
   */
  private <T> T call(final String operation, final String key, final Supplier<T> request)
      throws IOException {
    try {
      return request.get();
    } catch (final S3Exception e) {
      if (e.statusCode() == INVALID_RANGE) {
        throw new InvalidRangeException(e);
      }
      if (!(e instanceof NoSuchBucketException)
          && (e instanceof NoSuchKeyException || e.statusCode() == NOT_FOUND)) {
        throw new NoSuchObjectException(key, e);
      }
      throw new ObjectStoreException(operation, describe(key), e);
    } catch (final SdkException e) {
      throw new ObjectStoreException(operation, describe(key), e);
    }
  }

  /** The URI of a key or prefix, for a message, even if it isn't one of ours and has no URI. */
  private URI describe(final String key) {
    try {
      return uri(key);
    } catch (final IllegalArgumentException e) {
      return s3Uri(keyPrefix + key);
    }
  }

  /** The URI of a path in the bucket, with characters that a URI doesn't allow quoted. */
  private URI s3Uri(final String path) {
    try {
      return new URI("s3", bucket, "/" + path, null);
    } catch (final URISyntaxException e) {
      throw new InvalidStoreConfigException(
          "bucket and basePath", "to make a URI", bucket + "/" + path);
    }
  }

  /** The synchronous client, set up like the S3 backup store's, with the SDK's own retries. */
  static S3Client buildClient(final S3StoreConfig config) {
    final var builder = S3Client.builder();
    // Not AUTO, as the backup store has it: that asks the EC2 instance metadata service where it
    // runs, which takes two seconds to fail anywhere else, on every client that is built.
    builder.defaultsMode(DefaultsMode.STANDARD);
    builder.httpClientBuilder(
        ApacheHttpClient.builder()
            .tcpKeepAlive(true)
            .connectionAcquisitionTimeout(Duration.ofSeconds(30)));
    builder.overrideConfiguration(cfg -> cfg.retryStrategy(RetryMode.ADAPTIVE_V2));
    builder.forcePathStyle(config.forcePathStyle());
    if (config.endpoint() != null) {
      builder.endpointOverride(config.endpoint());
    }
    if (config.region() != null) {
      builder.region(Region.of(config.region()));
    }
    if (config.accessKeyId() != null && config.secretAccessKey() != null) {
      builder.credentialsProvider(
          StaticCredentialsProvider.create(
              AwsBasicCredentials.create(config.accessKeyId(), config.secretAccessKey())));
    }
    return builder.build();
  }

  /** The object is shorter than where a range starts. */
  private static final class InvalidRangeException extends IOException {
    InvalidRangeException(final Throwable cause) {
      super(cause);
    }
  }
}
