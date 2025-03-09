/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.document.store.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.camunda.document.api.*;
import io.camunda.document.api.DocumentError.DocumentAlreadyExists;
import io.camunda.document.api.DocumentError.DocumentHashMismatch;
import io.camunda.document.api.DocumentError.DocumentNotFound;
import io.camunda.document.api.DocumentError.InvalidInput;
import io.camunda.document.api.DocumentError.UnknownDocumentError;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.HttpStatusCode;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@ExtendWith(MockitoExtension.class)
class AwsDocumentStoreTest {

  public static final String BUCKET_NAME = "test-bucket";
  public static final Long BUCKET_TTL = 30L;
  public static final String BUCKET_PATH = "/test/";

  @Mock private S3Client s3Client;
  @Mock private S3Presigner preSigner;
  private AwsDocumentStore documentStore;

  @BeforeEach
  void setUp() {
    documentStore =
        new AwsDocumentStore(
            BUCKET_NAME,
            BUCKET_TTL,
            BUCKET_PATH,
            s3Client,
            Executors.newSingleThreadExecutor(),
            preSigner);
  }

  @Test
  void createDocumentShouldSucceed() {
    // given
    final var documentId = "test-document-id";
    final var content = "test-content-random-bits\n.".getBytes();
    final var inputStream = new ByteArrayInputStream(content);

    final var metadata =
        new DocumentMetadataModel(
            "text/plain",
            "test-file.txt",
            null,
            (long) content.length,
            null,
            null,
            Collections.emptyMap());

    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenThrow(S3Exception.builder().statusCode(HttpStatusCode.NOT_FOUND).build());

    final var mockPutResponse = mock(PutObjectResponse.class);
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .then(
            invocation -> {
              final var requestBody = (RequestBody) invocation.getArguments()[1];
              try (final var stream = requestBody.contentStreamProvider().newStream()) {
                stream.transferTo(OutputStream.nullOutputStream());
              }
              return null;
            })
        .thenReturn(mockPutResponse);
    when(s3Client.copyObject(any(CopyObjectRequest.class)))
        .thenReturn(mock(CopyObjectResponse.class));

    final var request = new DocumentCreationRequest(documentId, inputStream, metadata);

    // when
    final var result = documentStore.createDocument(request).join();

    // then
    assertTrue(result.isRight());
    assertEquals(documentId, result.get().documentId());

    final var putRequestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3Client).putObject(putRequestCaptor.capture(), any(RequestBody.class));
    final var putRequest = putRequestCaptor.getValue();
    assertThat(putRequest.key()).isEqualTo(BUCKET_PATH + documentId);
    assertThat(putRequest.bucket()).isEqualTo(BUCKET_NAME);
    assertThat(putRequest.metadata().get("content-hash")).isEqualTo("");

    final var copyRequestCaptor = ArgumentCaptor.forClass(CopyObjectRequest.class);
    verify(s3Client).copyObject(copyRequestCaptor.capture());
    final var copyRequest = copyRequestCaptor.getValue();
    assertThat(copyRequest.sourceKey()).isEqualTo(BUCKET_PATH + documentId);
    assertThat(copyRequest.destinationKey()).isEqualTo(BUCKET_PATH + documentId);
    assertThat(copyRequest.sourceBucket()).isEqualTo(BUCKET_NAME);
    assertThat(copyRequest.destinationBucket()).isEqualTo(BUCKET_NAME);
    assertThat(copyRequest.metadata().get("content-hash"))
        .isEqualTo("3635e7279b883d6bfd13cfe4d8815cc01b70c678afc8d278c0a4c1b0afbb87a8");
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
  void createDocumentShouldApplyTagIfDocumentExpiryGreaterThanTTL() {
    // given
    final var documentId = "existing-document-id";
    final var inputStream = new ByteArrayInputStream(new byte[0]);
    final ArgumentCaptor<PutObjectRequest> putObjectRequestCaptor = ArgumentCaptor.captor();
    final var expiryTime = OffsetDateTime.now().plus(Duration.ofDays(60));
    final var metadata =
        new DocumentMetadataModel(
            "application/text",
            "given-test-document.jpeg",
            expiryTime,
            10000L,
            null,
            null,
            Collections.emptyMap());

    final var request = new DocumentCreationRequest(documentId, inputStream, metadata);

    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenThrow(S3Exception.builder().statusCode(HttpStatusCode.NOT_FOUND).build());

    // when
    documentStore.createDocument(request).join();

    // then
    verify(s3Client).putObject(putObjectRequestCaptor.capture(), any(RequestBody.class));
    assertEquals("NoAutoDelete=true", putObjectRequestCaptor.getValue().tagging());
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
    final var expectedResponse = new DocumentContent(responseInputStream, null);

    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseInputStream);

    // when
    final var result = documentStore.getDocument(documentId).join();

    // then
    assertTrue(result.isRight());
    assertEquals(expectedResponse, result.get());
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
  void getDocumentShouldFailIfDocumentExpired() {
    // given
    final var documentId = "test-document-id";
    final String expiresAt = OffsetDateTime.now().minus(Duration.ofDays(10)).toString();
    final var metadata = Map.of("expires-at", expiresAt);

    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenReturn(HeadObjectResponse.builder().metadata(metadata).build());

    // when
    final var result = documentStore.getDocument(documentId).join();

    // then
    assertTrue(result.isLeft());
    assertInstanceOf(DocumentNotFound.class, result.getLeft());
  }

  @Test
  void verifyContentHashShouldSucceed() {
    // given
    final var documentId = "existing-document-id";
    final var contentHash = "randomhash";
    final var metadata = Map.of("content-hash", contentHash);

    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenReturn(HeadObjectResponse.builder().metadata(metadata).build());

    // when
    final var result = documentStore.verifyContentHash(documentId, contentHash).join();

    // then
    assertTrue(result.isRight());
  }

  @Test
  void verifyContentHashShouldFailForDifferentHash() {
    // given
    final var documentId = "existing-document-id";
    final var contentHash = "contentHash";
    final var metadata = Map.of("content-hash", contentHash);

    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenReturn(HeadObjectResponse.builder().metadata(metadata).build());

    // when
    final var result = documentStore.verifyContentHash(documentId, "wronHash").join();

    // then
    assertTrue(result.isLeft());
    assertInstanceOf(DocumentHashMismatch.class, result.getLeft());
  }

  @Test
  void verifyContentHashShouldFailIfDocumentDoesNotExist() {
    // given
    final var documentId = "existing-document-id";
    final var contentHash = "contentHash";

    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenThrow(S3Exception.builder().statusCode(HttpStatusCode.NOT_FOUND).build());

    // when
    final var result = documentStore.verifyContentHash(documentId, contentHash).join();

    // then
    assertTrue(result.isLeft());
    assertInstanceOf(DocumentNotFound.class, result.getLeft());
  }

  @Test
  void verifyContentHashShouldFailIfContentHashDoesNotExist() {
    // given
    final var documentId = "existing-document-id";
    final var contentHash = "contentHash";

    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenReturn(HeadObjectResponse.builder().build());

    // when
    final var result = documentStore.verifyContentHash(documentId, contentHash).join();

    // then
    assertTrue(result.isLeft());
    assertInstanceOf(InvalidInput.class, result.getLeft());
  }

  @Test
  void verifyContentHashShouldFailIfMetadataDoesNotExist() {
    // given
    final var documentId = "existing-document-id";
    final var contentHash = "contentHash";

    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenReturn(HeadObjectResponse.builder().metadata(Collections.emptyMap()).build());

    // when
    final var result = documentStore.verifyContentHash(documentId, contentHash).join();

    // then
    assertTrue(result.isLeft());
    assertInstanceOf(InvalidInput.class, result.getLeft());
  }

  @Test
  void verifyContentHashShouldFailForGeneralException() {
    // given
    final var documentId = "existing-document-id";
    final var contentHash = "contentHash";

    when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(new RuntimeException());

    // when
    final var result = documentStore.verifyContentHash(documentId, contentHash).join();

    // then
    assertTrue(result.isLeft());
    assertInstanceOf(UnknownDocumentError.class, result.getLeft());
  }

  @Test
  void createLinkShouldFailForInvalidDuration() {
    // given
    final var documentId = "valid-document-id";
    final var durationInMillis = -1L; // Invalid duration

    // when
    final var result = documentStore.createLink(documentId, durationInMillis).join();

    // then
    assertTrue(result.isLeft());
    assertInstanceOf(InvalidInput.class, result.getLeft());
    assertEquals("Duration must be greater than 0", ((InvalidInput) result.getLeft()).message());
  }

  @Test
  void createLinkShouldFailForDocumentNotFound() {
    // given
    final var documentId = "non-existing-document-id";
    final var durationInMillis = 10000L;

    when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(null);

    // when
    final var result = documentStore.createLink(documentId, durationInMillis).join();

    // then
    assertTrue(result.isLeft());
    assertInstanceOf(DocumentNotFound.class, result.getLeft());
  }

  @Test
  void createLinkShouldFailForExpiredDocument() {
    // given
    final var documentId = "expired-document-id";
    final var durationInMillis = 10000L; // Valid duration
    final var metadata =
        Map.of("expires-at", OffsetDateTime.now().minusDays(1).toString()); // yesterday

    final HeadObjectResponse documentInfo = HeadObjectResponse.builder().metadata(metadata).build();
    when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(documentInfo);

    // when
    final var result = documentStore.createLink(documentId, durationInMillis).join();

    // then
    assertTrue(result.isLeft());
    assertInstanceOf(DocumentNotFound.class, result.getLeft());
  }

  @Test
  void createLinkShouldSucceedWithValidDocument() throws MalformedURLException {
    // given
    final var documentId = "valid-document-id";
    final var durationInMillis = 10000L; // Valid duration
    final var metadata = Map.of("expires-at", OffsetDateTime.now().plusDays(1).toString());

    final HeadObjectResponse documentInfo = HeadObjectResponse.builder().metadata(metadata).build();
    when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(documentInfo);

    final PresignedGetObjectRequest preSignedRequest = mock(PresignedGetObjectRequest.class);
    when(preSigner.presignGetObject(any(GetObjectPresignRequest.class)))
        .thenReturn(preSignedRequest);
    when(preSignedRequest.url()).thenReturn(URI.create("https://example.com").toURL());

    final Instant expiration = Instant.now().plusMillis(durationInMillis);

    // when
    final var result = documentStore.createLink(documentId, durationInMillis).join();

    // then
    assertTrue(result.isRight());
    final DocumentLink documentLink = result.get();
    assertEquals("https://example.com", documentLink.link());

    // Assert expiration time is within 1 second of the expected expiration time
    final OffsetDateTime expectedExpiration =
        OffsetDateTime.ofInstant(expiration, ZoneId.systemDefault());
    final OffsetDateTime actualExpiration = documentLink.expiresAt();
    final Duration durationBetween = Duration.between(expectedExpiration, actualExpiration);

    // Assert that the difference between expected and actual expiration is within 1 second
    assertTrue(
        durationBetween.abs().getSeconds() <= 1, "Expiration times differ by more than 1 second");
  }

  @Test
  void createLinkShouldHandleGeneralException() {
    // given
    final var documentId = "valid-document-id";
    final var durationInMillis = 10000L; // Valid duration

    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenThrow(new RuntimeException("Unexpected error"));

    // when
    final var result = documentStore.createLink(documentId, durationInMillis).join();

    // then
    assertTrue(result.isLeft());
    assertInstanceOf(UnknownDocumentError.class, result.getLeft());
  }

  @Test
  void validateSetupShouldHandleExceptionIfExceptionIsThrown() {
    // given
    when(s3Client.headBucket(any(HeadBucketRequest.class)))
        .thenThrow(new RuntimeException("Unexpected error"));
    final var requestCaptor = ArgumentCaptor.forClass(HeadBucketRequest.class);

    // when
    assertThatNoException().isThrownBy(() -> documentStore.validateSetup());

    // then
    verify(s3Client).headBucket(requestCaptor.capture());
    verifyNoMoreInteractions(s3Client);
    final var request = requestCaptor.getValue();
    assertThat(request.bucket()).isEqualTo(BUCKET_NAME);
  }
}
