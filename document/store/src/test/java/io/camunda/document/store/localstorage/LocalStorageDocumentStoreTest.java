/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.localstorage;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.document.api.*;
import io.camunda.document.api.DocumentError.*;
import io.camunda.document.store.DocumentHashProcessor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.apache.commons.codec.digest.MessageDigestAlgorithms;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocalStorageDocumentStoreTest {

  private Path STORAGE_PATH;
  private LocalStorageDocumentStore documentStore;
  private final FileHandler fileHandler = new FileHandler();
  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() throws IOException {
    STORAGE_PATH = Files.createTempDirectory("test-storage");
    documentStore =
        new LocalStorageDocumentStore(
            STORAGE_PATH, fileHandler, new ObjectMapper(), Executors.newSingleThreadExecutor());
  }

  @AfterEach
  void cleanUp() throws IOException {
    deleteDirectoryRecursively(STORAGE_PATH);
  }

  @Test
  void createDocumentShouldSucceed() throws IOException {
    // given
    final String documentId = UUID.randomUUID().toString();
    final byte[] content = "test-content".getBytes();
    final ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
    final DocumentMetadataModel metadata = givenMetadata(content);
    final DocumentCreationRequest request =
        new DocumentCreationRequest(documentId, inputStream, metadata);

    // when
    final var result = documentStore.createDocument(request).join();

    // then
    assertTrue(result.isRight());
    assertEquals(documentId, result.get().documentId());
  }

  @Test
  void createDocumentShouldFailIfDocumentAlreadyExists() throws IOException {
    // given
    final String documentId = UUID.randomUUID().toString();
    final byte[] content = "test-content".getBytes();
    createDocumentForTest(content, documentId); // create a document to simulate existing document

    final DocumentCreationRequest request =
        new DocumentCreationRequest(
            documentId, new ByteArrayInputStream(content), givenMetadata(content));

    // when
    final var result = documentStore.createDocument(request).join();

    // then
    assertTrue(result.isLeft());
    assertInstanceOf(DocumentAlreadyExists.class, result.getLeft());
  }

  @Test
  void getDocumentShouldSucceed() throws IOException {
    // given
    final String documentId = UUID.randomUUID().toString();
    final byte[] content = "test-content".getBytes();
    createDocumentForTest(content, documentId);

    // when
    final var result = documentStore.getDocument(documentId).join();

    // then
    assertTrue(result.isRight());
    assertNotNull(result.get().inputStream());
  }

  @Test
  void getDocumentShouldFailIfDocumentNotFound() {
    // given
    final String documentId = UUID.randomUUID().toString();

    // when
    final var result = documentStore.getDocument(documentId).join();

    // then
    assertTrue(result.isLeft());
    assertInstanceOf(DocumentNotFound.class, result.getLeft());
  }

  @Test
  void deleteDocumentShouldSucceed() {
    // given
    final String documentId = UUID.randomUUID().toString();
    final byte[] content = "test-content".getBytes();
    createDocumentForTest(content, documentId);

    // when
    final var result = documentStore.deleteDocument(documentId).join();

    // then
    assertTrue(result.isRight());
    assertFalse(Files.exists(STORAGE_PATH.resolve(documentId)));
    assertFalse(
        Files.exists(STORAGE_PATH.resolve(documentId + LocalStorageDocumentStore.METADATA_SUFFIX)));
  }

  @Test
  void createLinkShouldFail() {
    // given
    final String documentId = UUID.randomUUID().toString();
    final long durationInMillis = 10000L;

    // when
    final var result = documentStore.createLink(documentId, durationInMillis).join();

    // then
    assertTrue(result.isLeft());
    assertInstanceOf(OperationNotSupported.class, result.getLeft());
  }

  @Test
  void verifyContentHashShouldSucceed() throws IOException {
    // given
    final String documentId = UUID.randomUUID().toString();
    final byte[] content = "test-content".getBytes();
    createDocumentForTest(content, documentId);

    final String contentHash =
        DocumentHashProcessor.hash(
                new ByteArrayInputStream(content), MessageDigestAlgorithms.SHA_256)
            .contentHash();

    // when
    final var result = documentStore.verifyContentHash(documentId, contentHash).join();

    // then
    assertTrue(result.isRight());
  }

  @Test
  void verifyContentHashShouldFailIfHashDoesNotMatch() throws IOException {
    // given
    final String documentId = UUID.randomUUID().toString();
    final byte[] content = "test-content".getBytes();
    createDocumentForTest(content, documentId);

    // when
    final var result = documentStore.verifyContentHash(documentId, "incorrect-hash").join();

    // then
    assertTrue(result.isLeft());
    assertInstanceOf(DocumentHashMismatch.class, result.getLeft());
  }

  @Test
  void verifyContentHashShouldFailIfDocumentNotFound() {
    // given
    final String documentId = UUID.randomUUID().toString();

    // when
    final var result = documentStore.verifyContentHash(documentId, "contentHash").join();

    // then
    assertTrue(result.isLeft());
    assertInstanceOf(DocumentNotFound.class, result.getLeft());
  }

  private void deleteDirectoryRecursively(final Path directory) throws IOException {
    if (Files.exists(directory)) {
      Files.walkFileTree(
          directory,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
                throws IOException {
              Files.delete(file);
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path dir, final IOException exc)
                throws IOException {
              Files.delete(dir);
              return FileVisitResult.CONTINUE;
            }
          });
    }
  }

  private void createDocumentForTest(final byte[] content, final String documentId) {
    final ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
    final DocumentMetadataModel metadata = givenMetadata(content);
    final DocumentCreationRequest request =
        new DocumentCreationRequest(documentId, inputStream, metadata);
    documentStore.createDocument(request).join();
  }

  private DocumentMetadataModel givenMetadata(final byte[] content) {
    return new DocumentMetadataModel(
        "text/plain", "test-file.txt", null, (long) content.length, null, null, null);
  }
}
