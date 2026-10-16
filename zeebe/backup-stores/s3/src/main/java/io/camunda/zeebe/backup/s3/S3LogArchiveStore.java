/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import software.amazon.awssdk.core.FileTransformerConfiguration;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * {@link LogArchiveStore} on an S3 bucket. Keys are prefixed with the configured base path. SSE-C
 * is applied by the client built by {@link S3BackupStore#buildClient(S3BackupConfig)}, so it covers
 * all requests made here.
 */
public final class S3LogArchiveStore implements LogArchiveStore {

  private final S3AsyncClient client;
  private final String bucket;
  private final String basePrefix;

  S3LogArchiveStore(final S3BackupConfig config, final S3AsyncClient client) {
    this.client = client;
    bucket = config.bucketName();
    basePrefix = config.basePath().map(base -> base + "/").orElse("");
  }

  public static S3LogArchiveStore of(final S3BackupConfig config) {
    return new S3LogArchiveStore(config, S3BackupStore.buildClient(config));
  }

  @Override
  public CompletableFuture<Void> put(final String key, final byte[] content) {
    return client
        .putObject(r -> r.bucket(bucket).key(basePrefix + key), AsyncRequestBody.fromBytes(content))
        .thenApply(r -> null);
  }

  @Override
  public CompletableFuture<Void> putFile(final String key, final Path file) {
    return client
        .putObject(r -> r.bucket(bucket).key(basePrefix + key), AsyncRequestBody.fromFile(file))
        .thenApply(r -> null);
  }

  @Override
  public CompletableFuture<Optional<byte[]>> getIfExists(final String key) {
    return client
        .getObject(r -> r.bucket(bucket).key(basePrefix + key), AsyncResponseTransformer.toBytes())
        .thenApply(response -> Optional.of(response.asByteArray()))
        .exceptionallyCompose(
            t -> isNotFound(t) ? CompletableFuture.completedFuture(Optional.empty()) : failed(t));
  }

  @Override
  public CompletableFuture<byte[]> getRange(final String key, final long offset, final int length) {
    if (length <= 0) {
      return CompletableFuture.completedFuture(new byte[0]);
    }
    final var range = "bytes=" + offset + "-" + (offset + length - 1);
    return client
        .getObject(
            r -> r.bucket(bucket).key(basePrefix + key).range(range),
            AsyncResponseTransformer.toBytes())
        .thenApply(response -> response.asByteArray())
        .exceptionallyCompose(
            t -> isInvalidRange(t) ? CompletableFuture.completedFuture(new byte[0]) : failed(t));
  }

  @Override
  public CompletableFuture<Void> getToFile(final String key, final Path target) {
    return client
        .getObject(
            r -> r.bucket(bucket).key(basePrefix + key),
            AsyncResponseTransformer.toFile(
                target, FileTransformerConfiguration.defaultCreateOrReplaceExisting()))
        .thenApply(r -> null);
  }

  @Override
  public CompletableFuture<List<String>> list(final String prefix) {
    final var keys = new java.util.ArrayList<String>();
    return client
        .listObjectsV2Paginator(r -> r.bucket(bucket).prefix(basePrefix + prefix))
        .subscribe(
            page ->
                page.contents().stream()
                    .map(S3Object::key)
                    .map(k -> k.substring(basePrefix.length()))
                    .forEach(keys::add))
        .thenApply(
            ignored -> {
              // S3 returns keys in UTF-8 binary order; sort to guarantee the contract regardless
              // of the S3-compatible implementation.
              java.util.Collections.sort(keys);
              return List.copyOf(keys);
            });
  }

  @Override
  public CompletableFuture<Void> closeAsync() {
    return CompletableFuture.runAsync(client::close);
  }

  private static <T> CompletableFuture<T> failed(final Throwable t) {
    return CompletableFuture.failedFuture(t);
  }

  private static boolean isNotFound(final Throwable t) {
    final var cause = unwrap(t);
    return cause instanceof NoSuchKeyException
        || cause instanceof S3Exception s3 && s3.statusCode() == 404;
  }

  private static boolean isInvalidRange(final Throwable t) {
    return unwrap(t) instanceof final S3Exception s3 && s3.statusCode() == 416;
  }

  private static Throwable unwrap(final Throwable t) {
    return t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
  }
}
