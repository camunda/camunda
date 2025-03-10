/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.gcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import io.camunda.document.api.DocumentContent;
import io.camunda.document.api.DocumentCreationRequest;
import io.camunda.document.api.DocumentError;
import io.camunda.document.api.DocumentError.UnknownDocumentError;
import io.camunda.document.api.DocumentLink;
import io.camunda.document.api.DocumentMetadataModel;
import io.camunda.document.api.DocumentReference;
import io.camunda.document.api.DocumentStore;
import io.camunda.document.store.InputStreamHashCalculator;
import io.camunda.zeebe.util.Either;
import java.nio.channels.Channels;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GcpDocumentStore implements DocumentStore {

  private static final Logger LOGGER = LoggerFactory.getLogger(GcpDocumentStore.class);

  private static final String CONTENT_HASH_METADATA_KEY = "contentHash";
  private static final String METADATA_PROCESS_DEFINITION_ID = "camunda.processDefinitionId";
  private static final String METADATA_PROCESS_INSTANCE_KEY = "camunda.processInstanceKey";

  private final String bucketName;
  private final String prefix;
  private final Storage storage;
  private final ObjectMapper objectMapper;
  private final ExecutorService executor;

  public GcpDocumentStore(
      final String bucketName, final String prefix, final ExecutorService executor) {
    this(bucketName, prefix, new ObjectMapper(), executor);
  }

  public GcpDocumentStore(
      final String bucketName,
      final String prefix,
      final ObjectMapper objectMapper,
      final ExecutorService executor) {
    this.bucketName = bucketName;
    this.prefix = prefix;
    storage = StorageOptions.getDefaultInstance().getService();
    this.objectMapper = objectMapper;
    this.executor = executor;
  }

  public GcpDocumentStore(
      final String bucketName,
      final String prefix,
      final Storage storage,
      final ObjectMapper objectMapper,
      final ExecutorService executor) {
    this.bucketName = bucketName;
    this.prefix = prefix;
    this.storage = storage;
    this.objectMapper = objectMapper;
    this.executor = executor;
  }

  @Override
  public CompletableFuture<Either<DocumentError, DocumentReference>> createDocument(
      final DocumentCreationRequest request) {
    return CompletableFuture.supplyAsync(() -> createDocumentInternal(request), executor);
  }

  @Override
  public CompletableFuture<Either<DocumentError, DocumentContent>> getDocument(
      final String documentId) {
    return CompletableFuture.supplyAsync(() -> getDocumentContentInternal(documentId), executor);
  }

  @Override
  public CompletableFuture<Either<DocumentError, Void>> deleteDocument(final String documentId) {
    return CompletableFuture.supplyAsync(() -> deleteDocumentInternal(documentId), executor);
  }

  @Override
  public CompletableFuture<Either<DocumentError, DocumentLink>> createLink(
      final String documentId, final long durationInMillis) {
    return CompletableFuture.supplyAsync(
        () -> createLinkInternal(documentId, durationInMillis), executor);
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
      if (storage.get(bucketName).exists()) {
        LOGGER.info("Successfully accessed bucket '{}'", bucketName);
      } else {
        LOGGER.warn("Bucket '{}' does not exist. {}", bucketName, SETUP_VALIDATION_FAILURE_MESSAGE);
      }
    } catch (final StorageException e) {
      LOGGER.warn(
          "Could not access bucket '{}' with the given credentials. {}",
          bucketName,
          SETUP_VALIDATION_FAILURE_MESSAGE,
          e);
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
    final String documentId =
        Optional.ofNullable(request.documentId()).orElse(UUID.randomUUID().toString());
    final String fullBlobName = getFullBlobName(documentId);
    final String fileName = Optional.ofNullable(request.metadata().fileName()).orElse(documentId);

    try {
      final Blob existingBlob = storage.get(bucketName, fullBlobName);
      if (existingBlob != null) {
        return Either.left(new DocumentError.DocumentAlreadyExists(documentId));
      }
    } catch (final Exception e) {
      return Either.left(new UnknownDocumentError(e));
    }

    final String hash;
    try {
      final BlobId blobId = BlobId.of(bucketName, fullBlobName);
      final var blobInfoBuilder = BlobInfo.newBuilder(blobId);
      applyMetadata(blobInfoBuilder, request.metadata(), fileName, "");
      hash =
          InputStreamHashCalculator.streamAndCalculateHash(
              request.contentInputStream(),
              stream -> storage.createFrom(blobInfoBuilder.build(), stream));

      applyMetadata(blobInfoBuilder, request.metadata(), fileName, hash);
      storage.update(blobInfoBuilder.build());
    } catch (final JsonProcessingException e) {
      return Either.left(
          new DocumentError.InvalidInput("Failed to serialize metadata: " + e.getMessage()));
    } catch (final Exception e) {
      return Either.left(new UnknownDocumentError(e));
    }

    final var updatedMetadata =
        new DocumentMetadataModel(
            request.metadata().contentType(),
            fileName,
            request.metadata().expiresAt(),
            request.metadata().size(),
            request.metadata().processDefinitionId(),
            request.metadata().processInstanceKey(),
            request.metadata().customProperties());

    final var documentReference = new DocumentReference(documentId, hash, updatedMetadata);
    return Either.right(documentReference);
  }

  private Either<DocumentError, DocumentContent> getDocumentContentInternal(
      final String documentId) {
    try {
      final String fullBlobName = getFullBlobName(documentId);
      final Blob blob = storage.get(bucketName, fullBlobName);
      if (blob == null) {
        return Either.left(new DocumentError.DocumentNotFound(documentId));
      }
      final var inputStream = Channels.newInputStream(blob.reader());
      final var contentType = blob.getContentType();
      return Either.right(new DocumentContent(inputStream, contentType));
    } catch (final Exception e) {
      return Either.left(new UnknownDocumentError(e));
    }
  }

  private Either<DocumentError, Void> deleteDocumentInternal(final String documentId) {
    try {
      final String fullBlobName = getFullBlobName(documentId);
      final boolean result = storage.delete(bucketName, fullBlobName);
      if (!result) {
        return Either.left(new DocumentError.DocumentNotFound(documentId));
      }
      return Either.right(null);
    } catch (final Exception e) {
      return Either.left(new UnknownDocumentError(e));
    }
  }

  private Either<DocumentError, DocumentLink> createLinkInternal(
      final String documentId, final long durationInMillis) {
    try {
      final String fullBlobName = getFullBlobName(documentId);
      final Blob blob = storage.get(bucketName, fullBlobName);
      if (blob == null) {
        return Either.left(new DocumentError.DocumentNotFound(documentId));
      }
      final var link = blob.signUrl(durationInMillis, TimeUnit.MILLISECONDS);
      return Either.right(
          new DocumentLink(
              link.toString(), OffsetDateTime.now().plus(Duration.ofMillis(durationInMillis))));
    } catch (final Exception e) {
      return Either.left(new UnknownDocumentError(e));
    }
  }

  private Either<DocumentError, Void> verifyContentHashInternal(
      final String documentId, final String contentHashToVerify) {
    try {
      final Blob blob = storage.get(bucketName, getFullBlobName(documentId));
      if (blob == null) {
        return Either.left(new DocumentError.DocumentNotFound(documentId));
      }
      final var metadata = blob.getMetadata();
      if (metadata == null) {
        return Either.left(new DocumentError.InvalidInput("No metadata found for document"));
      }
      final var storedContentHash = metadata.get(CONTENT_HASH_METADATA_KEY);
      if (storedContentHash == null) {
        return Either.left(new DocumentError.InvalidInput("No content hash found for document"));
      }
      if (!storedContentHash.equals(contentHashToVerify)) {
        return Either.left(new DocumentError.DocumentHashMismatch(documentId, contentHashToVerify));
      }
      return Either.right(null);
    } catch (final Exception e) {
      return Either.left(new UnknownDocumentError(e));
    }
  }

  private String getFullBlobName(final String documentId) {
    return Optional.ofNullable(prefix).orElse("") + documentId;
  }

  private void applyMetadata(
      final BlobInfo.Builder blobInfoBuilder,
      final DocumentMetadataModel metadata,
      final String fileName,
      final String contentHash)
      throws JsonProcessingException {
    if (metadata == null) {
      return;
    }
    if (metadata.contentType() != null && !metadata.contentType().isEmpty()) {
      blobInfoBuilder.setContentType(metadata.contentType());
    }
    if (metadata.expiresAt() != null) {
      blobInfoBuilder.setCustomTimeOffsetDateTime(metadata.expiresAt());
    }
    blobInfoBuilder.setContentDisposition("attachment; filename=" + fileName);

    final Map<String, String> blobMetadata = new HashMap<>();

    if (metadata.customProperties() != null && !metadata.customProperties().isEmpty()) {
      for (final var key : metadata.customProperties().keySet()) {
        final var value = metadata.customProperties().get(key);
        final var valueAsString = objectMapper.writeValueAsString(value);

        blobMetadata.put(key, valueAsString);
      }
    }
    if (metadata.processDefinitionId() != null) {
      blobMetadata.put(METADATA_PROCESS_DEFINITION_ID, metadata.processDefinitionId());
    }
    if (metadata.processInstanceKey() != null) {
      blobMetadata.put(METADATA_PROCESS_INSTANCE_KEY, metadata.processInstanceKey().toString());
    }
    blobMetadata.put(CONTENT_HASH_METADATA_KEY, contentHash);

    blobInfoBuilder.setMetadata(blobMetadata);
  }
}
