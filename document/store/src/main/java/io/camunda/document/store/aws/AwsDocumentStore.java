/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.aws;

import io.camunda.document.api.DocumentContent;
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
import io.camunda.document.store.InputStreamHashCalculator;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.HttpStatusCode;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.Tagging;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

public class AwsDocumentStore implements DocumentStore {

  public static final String CONTENT_HASH_METADATA_KEY = "content-hash";
  public static final String EXPIRES_AT_METADATA_KEY = "expires-at";
  public static final String FILENAME_METADATA_KEY = "filename";
  public static final String SIZE_METADATA_KEY = "size";
  public static final String CONTENT_TYPE_METADATA_KEY = "content-type";
  private static final Logger LOGGER = LoggerFactory.getLogger(AwsDocumentStore.class);
  private static final Tag NO_AUTO_DELETE_TAG =
      Tag.builder().key("NoAutoDelete").value("true").build();

  private static final String METADATA_PROCESS_DEFINITION_ID = "camunda.processDefinitionId";
  private static final String METADATA_PROCESS_INSTANCE_KEY = "camunda.processInstanceKey";

  private final String bucketName;
  private final S3Client client;
  private final ExecutorService executor;
  private final S3Presigner preSigner;
  private final Long defaultTTL;
  private final String bucketPath;

  public AwsDocumentStore(
      final String bucketName,
      final Long defaultTTL,
      final String bucketPath,
      final ExecutorService executor) {
    this(bucketName, defaultTTL, bucketPath, S3Client.create(), executor, S3Presigner.create());
  }

  public AwsDocumentStore(
      final String bucketName,
      final Long defaultTTL,
      final String bucketPath,
      final S3Client client,
      final ExecutorService executor,
      final S3Presigner preSigner) {
    this.bucketName = bucketName;
    this.defaultTTL = defaultTTL;
    this.bucketPath = bucketPath;
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
  public CompletableFuture<Either<DocumentError, DocumentContent>> getDocument(
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

  @Override
  public CompletableFuture<Either<DocumentError, Void>> verifyContentHash(
      final String documentId, final String contentHash) {
    return CompletableFuture.supplyAsync(
        () -> verifyContentHashInternal(documentId, contentHash), executor);
  }

  @Override
  public void validateSetup() {
    try {
      client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
      LOGGER.info("Successfully accessed bucket '{}'", bucketName);
    } catch (final NoSuchBucketException e) {
      LOGGER.warn("Bucket '{}' does not exist. {}", bucketName, SETUP_VALIDATION_FAILURE_MESSAGE);
    } catch (final S3Exception e) {
      LOGGER.warn(
          "Could not access bucket '{}'. {}", bucketName, SETUP_VALIDATION_FAILURE_MESSAGE, e);
    } catch (final Exception e) {
      LOGGER.warn(
          "Unexpected error while accessing bucket '{}'. {}",
          bucketName,
          SETUP_VALIDATION_FAILURE_MESSAGE,
          e);
    }
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

  private Either<DocumentError, DocumentContent> getDocumentInternal(final String documentId) {
    try {
      final GetObjectRequest getObjectRequest =
          GetObjectRequest.builder().key(resolveKey(documentId)).bucket(bucketName).build();

      final HeadObjectResponse documentInfo = getDocumentInfo(documentId);
      if (documentInfo != null && isDocumentExpired(documentInfo.metadata(), documentId)) {
        return Either.left(new DocumentNotFound(documentId));
      }

      final ResponseInputStream<GetObjectResponse> responseResponseInputStream =
          client.getObject(getObjectRequest);

      final String contentType =
          Optional.ofNullable(documentInfo).map(HeadObjectResponse::contentType).orElse(null);

      return Either.right(new DocumentContent(responseResponseInputStream, contentType));
    } catch (final Exception e) {
      return Either.left(getDocumentError(documentId, e));
    }
  }

  private Either<DocumentError, Void> deleteDocumentInternal(final String documentId) {
    try {
      client.deleteObject(
          DeleteObjectRequest.builder().bucket(bucketName).key(resolveKey(documentId)).build());

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
          GetObjectRequest.builder().bucket(bucketName).key(resolveKey(documentId)).build();

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

  private Either<DocumentError, Void> verifyContentHashInternal(
      final String documentId, final String contentHashToVerify) {
    try {
      final HeadObjectResponse documentInfo = getDocumentInfo(documentId);
      if (documentInfo == null) {
        return Either.left(new DocumentNotFound(documentId));
      }
      if (!documentInfo.hasMetadata()) {
        return Either.left(new InvalidInput("No metadata found for document"));
      }

      if (!documentInfo.metadata().containsKey(CONTENT_HASH_METADATA_KEY)) {
        return Either.left(new InvalidInput("No content hash found for document"));
      }

      if (!documentInfo
          .metadata()
          .get(CONTENT_HASH_METADATA_KEY.toLowerCase())
          .equals(contentHashToVerify)) {
        return Either.left(new DocumentError.DocumentHashMismatch(documentId, contentHashToVerify));
      }
      return Either.right(null);
    } catch (final Exception e) {
      return Either.left(new UnknownDocumentError(e));
    }
  }

  private HeadObjectResponse getDocumentInfo(final String documentId) {
    try {
      final HeadObjectRequest headObjectRequest =
          HeadObjectRequest.builder().bucket(bucketName).key(resolveKey(documentId)).build();

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
      final String expiresAt = metadata.get(EXPIRES_AT_METADATA_KEY);

      if (expiresAt != null && OffsetDateTime.parse(expiresAt).isBefore(OffsetDateTime.now())) {
        deleteDocumentInternal(documentId);
        return true;
      }
    }
    return false;
  }

  private Either<DocumentError, DocumentReference> uploadDocument(
      final DocumentCreationRequest request, final String documentId) {
    final String fileName = resolveFileName(request.metadata(), documentId);

    final var putObjectRequest =
        PutObjectRequest.builder()
            .key(resolveKey(documentId))
            .bucket(bucketName)
            .metadata(toS3MetaData(request.metadata(), fileName, ""))
            .contentType(request.metadata().contentType())
            .tagging(generateExpiryTag(request.metadata().expiresAt()))
            .build();

    final String hash;
    try {
      hash =
          InputStreamHashCalculator.streamAndCalculateHash(
              request.contentInputStream(),
              stream ->
                  client.putObject(
                      putObjectRequest,
                      RequestBody.fromInputStream(stream, request.metadata().size())));

      final var copyObjectRequest =
          CopyObjectRequest.builder()
              .sourceKey(resolveKey(documentId))
              .destinationKey(resolveKey(documentId))
              .sourceBucket(bucketName)
              .destinationBucket(bucketName)
              .metadata(toS3MetaData(request.metadata(), fileName, hash))
              .metadataDirective(MetadataDirective.REPLACE)
              .tagging(generateExpiryTag(request.metadata().expiresAt()))
              .contentType(request.metadata().contentType())
              .build();

      client.copyObject(copyObjectRequest);
    } catch (final Exception e) {
      return Either.left(new UnknownDocumentError(e));
    }

    final var updatedMetadata =
        new DocumentMetadataModel(
            request.metadata().contentType(),
            resolveFileName(request.metadata(), documentId),
            request.metadata().expiresAt(),
            request.metadata().size(),
            request.metadata().processDefinitionId(),
            request.metadata().processInstanceKey(),
            request.metadata().customProperties());
    return Either.right(new DocumentReference(documentId, hash, updatedMetadata));
  }

  private Map<String, String> toS3MetaData(
      final DocumentMetadataModel metadata, final String fileName, final String contentHash) {
    if (metadata == null) {
      return Collections.emptyMap();
    }

    final Map<String, String> metadataMap = new HashMap<>();

    putIfPresent(CONTENT_TYPE_METADATA_KEY, metadata.contentType(), metadataMap);
    putIfPresent(SIZE_METADATA_KEY, metadata.size(), metadataMap);
    putIfPresent(FILENAME_METADATA_KEY, fileName, metadataMap);
    putIfPresent(EXPIRES_AT_METADATA_KEY, metadata.expiresAt(), metadataMap);

    metadataMap.put(CONTENT_HASH_METADATA_KEY, contentHash);

    if (metadata.customProperties() != null) {
      metadata
          .customProperties()
          .forEach((key, value) -> metadataMap.put(key, String.valueOf(value)));
    }

    putIfPresent(METADATA_PROCESS_DEFINITION_ID, metadata.processDefinitionId(), metadataMap);
    putIfPresent(METADATA_PROCESS_INSTANCE_KEY, metadata.processInstanceKey(), metadataMap);

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

  private String resolveKey(final String documentId) {
    return bucketPath + documentId;
  }

  private String resolveFileName(
      final DocumentMetadataModel documentMetadata, final String documentId) {
    return documentMetadata.fileName() != null ? documentMetadata.fileName() : documentId;
  }
}
