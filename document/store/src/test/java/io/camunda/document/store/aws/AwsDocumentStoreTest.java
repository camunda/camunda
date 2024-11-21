/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.aws;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.camunda.document.api.*;
import io.camunda.document.api.DocumentError.DocumentAlreadyExists;
import io.camunda.document.api.DocumentError.DocumentNotFound;
import io.camunda.document.api.DocumentError.InvalidInput;
import io.camunda.document.api.DocumentError.UnknownDocumentError;
import java.io.ByteArrayInputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.Collections;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.HttpStatusCode;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@ExtendWith(MockitoExtension.class)
class AwsDocumentStoreTest {

  public static final String BUCKET_NAME = "test-bucket";

  @Mock private S3Client s3Client;
  @Mock private S3Presigner preSigner;
  private AwsDocumentStore documentStore;

  @BeforeEach
  void setUp() {
    documentStore =
        new AwsDocumentStore(BUCKET_NAME, s3Client, Executors.newSingleThreadExecutor(), preSigner);
  }

  @Test
  void createDocumentShouldSucceed() {
    // given
    final var documentId = "test-document-id";
    final var content = "test-content".getBytes();
    final var inputStream = new ByteArrayInputStream(content);

    final var metadata =
        new DocumentMetadataModel(
            "text/plain", "test-file.txt", null, (long) content.length, Collections.emptyMap());

    final var request = new DocumentCreationRequest(documentId, inputStream, metadata);

    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenThrow(S3Exception.builder().statusCode(HttpStatusCode.NOT_FOUND).build());
    final var mockPutResponse = mock(PutObjectResponse.class);
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(mockPutResponse);

    // when
    final var result = documentStore.createDocument(request).join();

    // then
    assertTrue(result.isRight());
    assertEquals(documentId, result.get().documentId());
    verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void createDocumentShouldFailIfDocumentAlreadyExists() {
    // given
    final var documentId = "existing-document-id";
    final var inputStream = new ByteArrayInputStream(new byte[0]);
    final var request = new DocumentCreationRequest(documentId, inputStream, null);

    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenReturn(HeadObjectResponse.builder().build());

    // when
    final var result = documentStore.createDocument(request).join();

    // then
    assertTrue(result.isLeft());
    assertInstanceOf(DocumentAlreadyExists.class, result.getLeft());
  }

  @Test
  void createDocumentShouldFailForGeneralException() {
    // given
    final var documentId = "existing-document-id";
    final var inputStream = new ByteArrayInputStream(new byte[0]);
    final var request = new DocumentCreationRequest(documentId, inputStream, null);

    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenThrow(new RuntimeException("Something went wrong"));

    // when
    final var result = documentStore.createDocument(request).join();

    // then
    assertTrue(result.isLeft());
    assertInstanceOf(UnknownDocumentError.class, result.getLeft());
  }

  @Test
  void getDocumentShouldSucceed() {
    // given
    final var documentId = "test-document-id";
    final var inputStream = new ByteArrayInputStream(new byte[0]);
    final var responseInputStream =
        new ResponseInputStream<>(GetObjectResponse.builder().build(), inputStream);

    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseInputStream);

    // when
    final var result = documentStore.getDocument(documentId).join();

    // then
    assertTrue(result.isRight());
    assertEquals(responseInputStream, result.get());
  }

  @Test
  void getDocumentShouldFailIfDocumentNotFound() {
    // given
    final var documentId = "test-document-id";

    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenThrow(S3Exception.builder().statusCode(HttpStatusCode.NOT_FOUND).build());

    // when
    final var result = documentStore.getDocument(documentId).join();

    // then
    assertTrue(result.isLeft());
    assertInstanceOf(DocumentNotFound.class, result.getLeft());
    verify(s3Client).getObject(any(GetObjectRequest.class));
  }

  @Test
  void deleteDocumentShouldSucceed() {
    // given
    final var documentId = "test-document-id";

    when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
        .thenReturn(mock(DeleteObjectResponse.class));

    // when
    final var result = documentStore.deleteDocument(documentId).join();

    // then
    assertFalse(result.isLeft());
  }

  @Test
  void deleteDocumentShouldFailForException() {
    // given
    final var documentId = "test-document-id";

    when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
        .thenThrow(new RuntimeException("Something went wrong"));

    // when
    final var result = documentStore.deleteDocument(documentId).join();

    // then
    assertTrue(result.isLeft());
    assertInstanceOf(UnknownDocumentError.class, result.getLeft());
  }

  @Test
  void createDocumentLinkShouldSucceed() throws MalformedURLException {
    // given
    final var documentId = "test-document-id";
    final var linkUrl = URI.create("http://awsurl/" + documentId).toURL();

    final var objectRequestMock = mock(PresignedGetObjectRequest.class);
    when(preSigner.presignGetObject(any(GetObjectPresignRequest.class)))
        .thenReturn(objectRequestMock);
    when(objectRequestMock.url()).thenReturn(linkUrl);

    // when
    final var result = documentStore.createLink(documentId, 10000).join();

    // then
    assertFalse(result.isLeft());
    assertEquals(linkUrl.toString(), result.get().link());
  }

  @Test
  void createDocumentLinkShouldFailForInvalidDuration() {
    // given
    final var documentId = "test-document-id";

    // when
    final var result = documentStore.createLink(documentId, -1).join();

    // then
    assertTrue(result.isLeft());
    assertInstanceOf(InvalidInput.class, result.getLeft());
  }

  @Test
  void createDocumentLinkShouldFailForException() {
    // given
    final var documentId = "test-document-id";
    when(preSigner.presignGetObject(any(GetObjectPresignRequest.class)))
        .thenThrow(new RuntimeException("Something went wrong"));

    // when
    final var result = documentStore.createLink(documentId, 10000).join();

    // then
    assertTrue(result.isLeft());
    assertInstanceOf(UnknownDocumentError.class, result.getLeft());
  }
}
