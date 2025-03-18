/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.localstorage;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.document.api.DocumentContent;
import io.camunda.document.api.DocumentCreationRequest;
import io.camunda.document.api.DocumentError;
import io.camunda.document.api.DocumentError.DocumentAlreadyExists;
import io.camunda.document.api.DocumentError.DocumentHashMismatch;
import io.camunda.document.api.DocumentError.DocumentNotFound;
import io.camunda.document.api.DocumentError.InvalidInput;
import io.camunda.document.api.DocumentError.OperationNotSupported;
import io.camunda.document.api.DocumentError.UnknownDocumentError;
import io.camunda.document.api.DocumentLink;
import io.camunda.document.api.DocumentMetadataModel;
import io.camunda.document.api.DocumentReference;
import io.camunda.document.api.DocumentStore;
import io.camunda.document.store.InputStreamHashCalculator;
import io.camunda.zeebe.util.Either;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;

public class LocalStorageDocumentStore implements DocumentStore {

  public static final String METADATA_SUFFIX = "-metadata";
  private static final Logger LOGGER =
      org.slf4j.LoggerFactory.getLogger(LocalStorageDocumentStore.class);
  private static final List<String> INVALID_DOCUMENT_ID_CHARACTERS = List.of("..", "/", "\\");
  private final Path storagePath;
  private final FileHandler fileHandler;
  private final ExecutorService executor;
  private final ObjectMapper mapper;

  public LocalStorageDocumentStore(
      final Path storagePath,
      final FileHandler fileHandler,
      final ObjectMapper mapper,
      final ExecutorService executor) {
    this.storagePath = storagePath;
    this.fileHandler = fileHandler;
    this.mapper = mapper;
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
    return CompletableFuture.completedFuture(
        Either.left(
            new OperationNotSupported(
                "The local storage document store does not support creating links")));
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
      if (Files.exists(storagePath)) {
        LOGGER.info("Successfully accessed storage path '{}'", storagePath);
      } else {
        LOGGER.warn(
            "Storage path '{}' does not exist. {}", storagePath, SETUP_VALIDATION_FAILURE_MESSAGE);
      }
    } catch (final Exception e) {
      LOGGER.warn(
          "Could not verify the existence of the storage path '{}'. {}",
          storagePath,
          SETUP_VALIDATION_FAILURE_MESSAGE,
          e);
    }
  }

  private Either<DocumentError, DocumentReference> createDocumentInternal(
      final DocumentCreationRequest request) {
    final String documentId = getDocumentId(request);
    if (!documentIdIsValid(documentId)) {
      return Either.left(new InvalidInput(generateInvalidDocumentIdMessage(documentId)));
    }
    final Path documentFilePath = storagePath.resolve(documentId);
    final Path documentMetaDataFilePath = storagePath.resolve(documentId + METADATA_SUFFIX);

    // remove inconsistent data if it exists (rare occurrence)
    if (fileHandler.fileExists(documentFilePath)
        != fileHandler.fileExists(documentMetaDataFilePath)) {
      try {
        fileHandler.delete(documentFilePath);
        fileHandler.delete(documentMetaDataFilePath);
      } catch (final IOException e) {
        LOGGER.warn("Error deleting document or metadata with document ID {}", documentId);
        return Either.left(new UnknownDocumentError(e));
      }
    }

    if (fileHandler.fileExists(documentFilePath)
        && fileHandler.fileExists(documentMetaDataFilePath)) {
      return Either.left(new DocumentAlreadyExists(documentId));
    }

    try {
      final var contentHash =
          InputStreamHashCalculator.streamAndCalculateHash(
              request.contentInputStream(),
              stream -> {
                try (final InputStream metadataStream =
                    new ByteArrayInputStream(mapper.writeValueAsBytes(request.metadata()))) {
                  fileHandler.createFile(stream, documentFilePath);
                  fileHandler.createFile(metadataStream, documentMetaDataFilePath);
                }
              });
      return Either.right(new DocumentReference(documentId, contentHash, request.metadata()));
    } catch (final Exception e) {
      return Either.left(new UnknownDocumentError(e));
    }
  }

  private Either<DocumentError, DocumentContent> getDocumentInternal(final String documentId) {
    if (!documentIdIsValid(documentId)) {
      return Either.left(new InvalidInput(generateInvalidDocumentIdMessage(documentId)));
    }
    final Path documentPath = storagePath.resolve(documentId);
    final Path documentMetadataPath = storagePath.resolve(documentId + METADATA_SUFFIX);

    if (!fileHandler.fileExists(documentPath) || !fileHandler.fileExists(documentMetadataPath)) {
      return Either.left(new DocumentNotFound(documentId));
    }

    try {
      final InputStream inputStream = fileHandler.getInputStream(documentPath);
      final DocumentMetadataModel metadataModel =
          mapper.readValue(documentMetadataPath.toFile(), DocumentMetadataModel.class);

      return Either.right(new DocumentContent(inputStream, metadataModel.contentType()));
    } catch (final IOException e) {
      return Either.left(
          new UnknownDocumentError(
              "Error occurred while retrieving document ID: " + documentId, e));
    }
  }

  private Either<DocumentError, Void> deleteDocumentInternal(final String documentId) {
    final Path documentPath = storagePath.resolve(documentId);
    final Path documentMetadataPath = storagePath.resolve(documentId + METADATA_SUFFIX);

    try {
      fileHandler.delete(documentPath);
      fileHandler.delete(documentMetadataPath);
    } catch (final IOException e) {
      return Either.left(new UnknownDocumentError(e));
    }
    return Either.right(null);
  }

  private Either<DocumentError, Void> verifyContentHashInternal(
      final String documentId, final String contentHash) {
    final Path documentPath = storagePath.resolve(documentId);

    if (!fileHandler.fileExists(documentPath)) {
      return Either.left(new DocumentNotFound(documentId));
    }

    try {
      final var calculatedHash =
          InputStreamHashCalculator.streamAndCalculateHash(
              fileHandler.getInputStream(documentPath));
      if (!calculatedHash.equals(contentHash)) {
        return Either.left(new DocumentHashMismatch(documentId, contentHash));
      }
    } catch (final Exception e) {
      return Either.left(new UnknownDocumentError(e));
    }
    return Either.right(null);
  }

  private static String getDocumentId(final DocumentCreationRequest request) {
    return request.documentId() == null ? UUID.randomUUID().toString() : request.documentId();
  }

  private boolean documentIdIsValid(final String documentId) {
    return INVALID_DOCUMENT_ID_CHARACTERS.stream().noneMatch(documentId::contains);
  }

  private String generateInvalidDocumentIdMessage(final String documentId) {
    return "Document ID '"
        + documentId
        + "' cannot contain the following: "
        + String.join(", ", INVALID_DOCUMENT_ID_CHARACTERS);
  }
}
