/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.azure;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.UserDelegationKey;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AzureBlobDocumentStore implements DocumentStore {

  private static final Logger LOGGER = LoggerFactory.getLogger(AzureBlobDocumentStore.class);

  private static final String CONTENT_HASH_METADATA_KEY = "contentHash";
  private static final String EXPIRES_AT_METADATA_KEY = "expiresAt";
  private static final String FILENAME_METADATA_KEY = "fileName";
  private static final String SIZE_METADATA_KEY = "size";
  private static final String CONTENT_TYPE_METADATA_KEY = "contentType";
  private static final String METADATA_PROCESS_DEFINITION_ID = "camundaProcessDefinitionId";
  private static final String METADATA_PROCESS_INSTANCE_KEY = "camundaProcessInstanceKey";

  private static final int HTTP_NOT_FOUND = 404;
  private static final int HTTP_FORBIDDEN = 403;
  private static final int HTTP_CONFLICT = 409;

  private final String containerName;
  private final String containerPath;
  private final BlobContainerClient containerClient;
  private final BlobServiceClient serviceClient;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ExecutorService executor;

  public AzureBlobDocumentStore(
      final String containerName,
      final String containerPath,
      final BlobContainerClient containerClient,
      final BlobServiceClient serviceClient,
      final ExecutorService executor) {
    this.containerName = containerName;
    this.containerPath = containerPath;
    this.containerClient = containerClient;
    this.serviceClient = serviceClient;
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
      if (containerClient.exists()) {
        LOGGER.info("Successfully accessed container '{}'", containerName);
      } else {
        LOGGER.warn(
            "Container '{}' does not exist. {}", containerName, SETUP_VALIDATION_FAILURE_MESSAGE);
      }
    } catch (final BlobStorageException e) {
      if (e.getStatusCode() == HTTP_FORBIDDEN) {
        LOGGER.warn(
            "Access denied to container '{}'. Check that the connection string or credentials "
                + "have the required permissions. {}",
            containerName,
            SETUP_VALIDATION_FAILURE_MESSAGE,
            e);
      } else if (e.getStatusCode() == HTTP_NOT_FOUND) {
        LOGGER.warn(
            "Container '{}' was not found. Check that the 'CONTAINER' property is correct. {}",
            containerName,
            SETUP_VALIDATION_FAILURE_MESSAGE,
            e);
      } else {
        LOGGER.warn(
            "Could not access container '{}' (HTTP {}). {}",
            containerName,
            e.getStatusCode(),
            SETUP_VALIDATION_FAILURE_MESSAGE,
            e);
      }
    } catch (final Exception e) {
      LOGGER.warn(
          "Unexpected error while accessing container '{}'. "
              + "Check that the connection string or endpoint is valid. {}",
          containerName,
          SETUP_VALIDATION_FAILURE_MESSAGE,
          e);
    }
  }

  private Either<DocumentError, DocumentReference> createDocumentInternal(
      final DocumentCreationRequest request) {
    final String documentId =
        Objects.requireNonNullElse(request.documentId(), UUID.randomUUID().toString());
    try {
      final String blobName = resolveBlobName(documentId);
      final BlobClient blobClient = containerClient.getBlobClient(blobName);

      if (blobClient.exists()) {
        return Either.left(new DocumentAlreadyExists(documentId));
      }

      final String fileName = resolveFileName(request.metadata(), documentId);

      final String hash;
      try {
        hash =
            InputStreamHashCalculator.streamAndCalculateHash(
                request.contentInputStream(),
                stream -> blobClient.upload(stream, request.metadata().size(), false));
      } catch (final Exception e) {
        return Either.left(new UnknownDocumentError(e));
      }

      final Map<String, String> metadata = toBlobMetadata(request.metadata(), fileName, hash);
      blobClient.setMetadata(metadata);

      final BlobHttpHeaders headers = new BlobHttpHeaders();
      if (request.metadata().contentType() != null) {
        headers.setContentType(request.metadata().contentType());
      }
      headers.setContentDisposition("attachment; filename=" + fileName);
      blobClient.setHttpHeaders(headers);

      final var updatedMetadata =
          new DocumentMetadataModel(
              request.metadata().contentType(),
              fileName,
              request.metadata().expiresAt(),
              request.metadata().size(),
              request.metadata().processDefinitionId(),
              request.metadata().processInstanceKey(),
              request.metadata().customProperties());
      return Either.right(new DocumentReference(documentId, hash, updatedMetadata));
    } catch (final BlobStorageException e) {
      return Either.left(mapBlobStorageError(documentId, e));
    } catch (final Exception e) {
      return Either.left(new UnknownDocumentError(e));
    }
  }

  private Either<DocumentError, DocumentContent> getDocumentInternal(final String documentId) {
    try {
      final String blobName = resolveBlobName(documentId);
      final BlobClient blobClient = containerClient.getBlobClient(blobName);

      final BlobProperties properties = blobClient.getProperties();

      if (isDocumentExpired(properties.getMetadata(), documentId)) {
        return Either.left(new DocumentNotFound(documentId));
      }

      final InputStream inputStream = blobClient.openInputStream();
      final String contentType = properties.getContentType();

      return Either.right(new DocumentContent(inputStream, contentType));
    } catch (final BlobStorageException e) {
      return Either.left(mapBlobStorageError(documentId, e));
    } catch (final Exception e) {
      return Either.left(new UnknownDocumentError(e));
    }
  }

  private Either<DocumentError, Void> deleteDocumentInternal(final String documentId) {
    try {
      final String blobName = resolveBlobName(documentId);
      final BlobClient blobClient = containerClient.getBlobClient(blobName);
      blobClient.delete();
      return Either.right(null);
    } catch (final BlobStorageException e) {
      return Either.left(mapBlobStorageError(documentId, e));
    } catch (final Exception e) {
      return Either.left(new UnknownDocumentError(e));
    }
  }

  private Either<DocumentError, DocumentLink> createLinkInternal(
      final String documentId, final long durationInMillis) {
    try {
      if (durationInMillis <= 0) {
        return Either.left(new InvalidInput("Duration must be greater than 0"));
      }

      final String blobName = resolveBlobName(documentId);
      final BlobClient blobClient = containerClient.getBlobClient(blobName);

      final BlobProperties properties;
      try {
        properties = blobClient.getProperties();
      } catch (final BlobStorageException e) {
        return Either.left(mapBlobStorageError(documentId, e));
      }

      if (isDocumentExpired(properties.getMetadata(), documentId)) {
        return Either.left(new DocumentNotFound(documentId));
      }

      final OffsetDateTime expiryTime =
          OffsetDateTime.now().plus(Duration.ofMillis(durationInMillis));
      final BlobSasPermission permissions = new BlobSasPermission().setReadPermission(true);
      final BlobServiceSasSignatureValues sasValues =
          new BlobServiceSasSignatureValues(expiryTime, permissions);

      String sasUrl;
      try {
        final UserDelegationKey userDelegationKey =
            serviceClient.getUserDelegationKey(OffsetDateTime.now().minusMinutes(5), expiryTime);
        final String sasToken = blobClient.generateUserDelegationSas(sasValues, userDelegationKey);
        sasUrl = blobClient.getBlobUrl() + "?" + sasToken;
      } catch (final Exception e) {
        LOGGER.debug("User delegation SAS generation failed, falling back to service SAS", e);
        final String sasToken = blobClient.generateSas(sasValues);
        sasUrl = blobClient.getBlobUrl() + "?" + sasToken;
      }

      return Either.right(new DocumentLink(sasUrl, expiryTime));
    } catch (final BlobStorageException e) {
      return Either.left(mapBlobStorageError(documentId, e));
    } catch (final Exception e) {
      return Either.left(new UnknownDocumentError(e));
    }
  }

  private Either<DocumentError, Void> verifyContentHashInternal(
      final String documentId, final String contentHashToVerify) {
    try {
      final String blobName = resolveBlobName(documentId);
      final BlobClient blobClient = containerClient.getBlobClient(blobName);

      final BlobProperties properties;
      try {
        properties = blobClient.getProperties();
      } catch (final BlobStorageException e) {
        return Either.left(mapBlobStorageError(documentId, e));
      }

      final Map<String, String> metadata = properties.getMetadata();
      if (metadata == null) {
        return Either.left(new InvalidInput("No metadata found for document"));
      }

      final String storedContentHash = metadata.get(CONTENT_HASH_METADATA_KEY);
      if (storedContentHash == null) {
        return Either.left(new InvalidInput("No content hash found for document"));
      }

      if (!storedContentHash.equals(contentHashToVerify)) {
        return Either.left(new DocumentError.DocumentHashMismatch(documentId, contentHashToVerify));
      }
      return Either.right(null);
    } catch (final BlobStorageException e) {
      return Either.left(mapBlobStorageError(documentId, e));
    } catch (final Exception e) {
      return Either.left(new UnknownDocumentError(e));
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

  private Map<String, String> toBlobMetadata(
      final DocumentMetadataModel metadata, final String fileName, final String contentHash)
      throws JsonProcessingException {
    if (metadata == null) {
      return Collections.emptyMap();
    }

    final Map<String, String> metadataMap = new HashMap<>();

    putIfPresent(CONTENT_TYPE_METADATA_KEY, metadata.contentType(), metadataMap);
    putIfPresent(SIZE_METADATA_KEY, metadata.size(), metadataMap);
    putIfPresent(FILENAME_METADATA_KEY, fileName, metadataMap);
    putIfPresent(EXPIRES_AT_METADATA_KEY, metadata.expiresAt(), metadataMap);

    metadataMap.put(CONTENT_HASH_METADATA_KEY, contentHash);

    if (metadata.customProperties() != null && !metadata.customProperties().isEmpty()) {
      for (final var key : metadata.customProperties().keySet()) {
        final var value = metadata.customProperties().get(key);
        final var valueAsString = objectMapper.writeValueAsString(value);
        metadataMap.put(key, valueAsString);
      }
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

  private static DocumentError mapBlobStorageError(
      final String documentId, final BlobStorageException e) {
    final int statusCode = e.getStatusCode();
    if (statusCode == HTTP_NOT_FOUND) {
      if (e.getErrorCode() != null && "ContainerNotFound".equals(e.getErrorCode().toString())) {
        return new UnknownDocumentError(
            "Azure Blob container not found. Check that the 'CONTAINER' property "
                + "is correct and the container exists.",
            e);
      }
      return new DocumentNotFound(documentId);
    }
    if (statusCode == HTTP_FORBIDDEN) {
      return new UnknownDocumentError(
          "Access denied to Azure Blob Storage. Check that the connection string "
              + "or credentials have the required permissions.",
          e);
    }
    if (statusCode == HTTP_CONFLICT) {
      return new DocumentAlreadyExists(documentId);
    }
    return new UnknownDocumentError(
        "Azure Blob Storage error (HTTP " + statusCode + "): " + e.getMessage(), e);
  }

  private String resolveBlobName(final String documentId) {
    return containerPath + documentId;
  }

  private String resolveFileName(
      final DocumentMetadataModel documentMetadata, final String documentId) {
    return documentMetadata.fileName() != null ? documentMetadata.fileName() : documentId;
  }
}
