/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.aws;

import io.camunda.document.api.DocumentCreationRequest;
import io.camunda.document.api.DocumentError;
import io.camunda.document.api.DocumentError.DocumentAlreadyExists;
import io.camunda.document.api.DocumentError.DocumentNotFound;
import io.camunda.document.api.DocumentError.InvalidInput;
import io.camunda.document.api.DocumentError.UnknownDocumentError;
import io.camunda.document.api.DocumentLink;
import io.camunda.document.api.DocumentMetadataModel;
import io.camunda.document.api.DocumentReference;
import io.camunda.document.api.DocumentStore;
import io.camunda.zeebe.util.Either;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.HttpStatusCode;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.Tagging;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

public class AwsDocumentStore implements DocumentStore {

  private static final Tag NO_AUTO_DELETE_TAG =
      Tag.builder().key("NoAutoDelete").value("true").build();

  private final String bucketName;
  private final S3Client client;
  private final ExecutorService executor;
  private final S3Presigner preSigner;
  private final Long defaultTTL;

  public AwsDocumentStore(final String bucketName, final Long defaultTTL) {
    this(
        bucketName,
        defaultTTL,
        S3Client.create(),
        Executors.newSingleThreadExecutor(),
        S3Presigner.create());
  }

  public AwsDocumentStore(
      final String bucketName,
      final Long defaultTTL,
      final S3Client client,
      final ExecutorService executor,
      final S3Presigner preSigner) {
    this.bucketName = bucketName;
    this.defaultTTL = defaultTTL;
    this.client = client;
    this.executor = executor;
    this.preSigner = preSigner;
  }

  @Override
  public CompletableFuture<Either<DocumentError, DocumentReference>> createDocument(
      final DocumentCreationRequest request) {
    return CompletableFuture.supplyAsync(() -> createDocumentInternal(request), executor);
  }

  @Override
  public CompletableFuture<Either<DocumentError, InputStream>> getDocument(
      final String documentId) {
    return CompletableFuture.supplyAsync(() -> getDocumentInternal(documentId), executor);
  }

  @Override
  public CompletableFuture<Either<DocumentError, Void>> deleteDocument(final String documentId) {
    return CompletableFuture.supplyAsync(() -> deleteDocumentInternal(documentId), executor);
  }

  @Override
  public CompletableFuture<Either<DocumentError, DocumentLink>> createLink(
      final String documentId, final long durationInMillis) {
    return CompletableFuture.supplyAsync(
        () -> linkDocumentInternal(documentId, durationInMillis), executor);
  }

  private Either<DocumentError, DocumentReference> createDocumentInternal(
      final DocumentCreationRequest request) {
    try {
      final String documentId =
          Objects.requireNonNullElse(request.documentId(), UUID.randomUUID().toString());

      final HeadObjectResponse documentInfo = getDocumentInfo(documentId);
      if (documentInfo != null) {
        return Either.left(new DocumentAlreadyExists(documentId));
      }

      return uploadDocument(request, documentId);
    } catch (final Exception e) {
      return Either.left(new UnknownDocumentError(e));
    }
  }

  private Either<DocumentError, InputStream> getDocumentInternal(final String documentId) {
    try {
      final GetObjectRequest getObjectRequest =
          GetObjectRequest.builder().key(documentId).bucket(bucketName).build();

      final HeadObjectResponse documentInfo = getDocumentInfo(documentId);
      if (documentInfo != null && isDocumentExpired(documentInfo.metadata(), documentId)) {
        return Either.left(new DocumentNotFound(documentId));
      }

      final ResponseInputStream<GetObjectResponse> responseResponseInputStream =
          client.getObject(getObjectRequest);

      return Either.right(responseResponseInputStream);
    } catch (final Exception e) {
      return Either.left(getDocumentError(documentId, e));
    }
  }

  private Either<DocumentError, Void> deleteDocumentInternal(final String documentId) {
    try {
      client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(documentId).build());

      return Either.right(null);
    } catch (final Exception e) {
      return Either.left(getDocumentError(documentId, e));
    }
  }

  private Either<DocumentError, DocumentLink> linkDocumentInternal(
      final String documentId, final long durationInMillis) {
    try {
      if (durationInMillis <= 0) {
        return Either.left(new InvalidInput("Duration must be greater than 0"));
      }

      final HeadObjectResponse documentInfo = getDocumentInfo(documentId);
      if (documentInfo == null || isDocumentExpired(documentInfo.metadata(), documentId)) {
        return Either.left(new DocumentNotFound(documentId));
      }

      final GetObjectRequest objectRequest =
          GetObjectRequest.builder().bucket(bucketName).key(documentId).build();

      final GetObjectPresignRequest preSignRequest =
          GetObjectPresignRequest.builder()
              .signatureDuration(Duration.ofMillis(durationInMillis))
              .getObjectRequest(objectRequest)
              .build();

      final PresignedGetObjectRequest preSignedRequest = preSigner.presignGetObject(preSignRequest);
      final Instant expiration = Instant.now().plusMillis(durationInMillis);

      return Either.right(
          new DocumentLink(
              preSignedRequest.url().toString(),
              OffsetDateTime.ofInstant(expiration, ZoneId.systemDefault())));
    } catch (final Exception e) {
      return Either.left(getDocumentError(documentId, e));
    }
  }

  private HeadObjectResponse getDocumentInfo(final String documentId) {
    try {
      final HeadObjectRequest headObjectRequest =
          HeadObjectRequest.builder().bucket(bucketName).key(documentId).build();

      return client.headObject(headObjectRequest);
    } catch (final S3Exception e) {
      if (e.statusCode() == HttpStatusCode.NOT_FOUND) {
        return null;
      }
      throw e;
    }
  }

  private boolean isDocumentExpired(final Map<String, String> metadata, final String documentId) {
    if (metadata != null) {
      final String expiresAt = metadata.get("expires-at");

      if (expiresAt != null && OffsetDateTime.parse(expiresAt).isBefore(OffsetDateTime.now())) {
        deleteDocumentInternal(documentId);
        return true;
      }
    }
    return false;
  }

  private Either<DocumentError, DocumentReference> uploadDocument(
      final DocumentCreationRequest request, final String documentId) {
    final PutObjectRequest putObjectRequest =
        PutObjectRequest.builder()
            .key(documentId)
            .bucket(bucketName)
            .metadata(toS3MetaData(request.metadata()))
            .tagging(generateExpiryTag(request.metadata().expiresAt()))
            .build();

    client.putObject(
        putObjectRequest,
        RequestBody.fromInputStream(request.contentInputStream(), request.metadata().size()));

    return Either.right(new DocumentReference(documentId, request.metadata()));
  }

  private Map<String, String> toS3MetaData(final DocumentMetadataModel metadata) {
    if (metadata == null) {
      return Collections.emptyMap();
    }

    final Map<String, String> metadataMap = new HashMap<>();

    putIfPresent("content-type", metadata.contentType(), metadataMap);
    putIfPresent("size", metadata.size(), metadataMap);
    putIfPresent("filename", metadata.fileName(), metadataMap);
    putIfPresent("expires-at", metadata.expiresAt(), metadataMap);

    if (metadata.customProperties() != null) {
      metadata
          .customProperties()
          .forEach((key, value) -> metadataMap.put(key, String.valueOf(value)));
    }

    return metadataMap;
  }

  private <T> void putIfPresent(
      final String key, final T value, final Map<String, String> metadataMap) {
    if (value != null) {
      metadataMap.put(key, value.toString());
    }
  }

  private static DocumentError getDocumentError(final String documentId, final Exception e) {
    if (e instanceof final S3Exception s3Exception
        && s3Exception.statusCode() == HttpStatusCode.NOT_FOUND) {
      return new DocumentNotFound(documentId);
    }
    return new UnknownDocumentError(e);
  }

  private Tagging generateExpiryTag(final OffsetDateTime expiryDate) {
    final boolean isExpiryDateBeyondBucketTTL =
        expiryDate != null
            && defaultTTL != null
            && expiryDate.isAfter(OffsetDateTime.now().plus(Duration.ofDays(defaultTTL)));

    return Tagging.builder()
        .tagSet(
            isExpiryDateBeyondBucketTTL
                ? Collections.singletonList(NO_AUTO_DELETE_TAG)
                : Collections.emptyList())
        .build();
  }
}
