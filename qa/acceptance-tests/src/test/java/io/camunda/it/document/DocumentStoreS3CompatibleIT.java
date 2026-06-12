/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbConfigurator;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * Verifies that Camunda Document Handling can be pointed at a self-hosted S3-compatible backend via
 * the {@code DOCUMENT_STORE_AWS_*} configuration properties.
 *
 * <p>Exercises both upload paths of {@link io.camunda.document.store.aws.AwsDocumentStore}: the
 * default streaming path (chunked encoding enabled, used by real AWS) and the buffered temp-file
 * path (chunked encoding disabled, required by some S3-compatible backends).
 *
 * <p>Assertions are made directly against the backend so a regression in the wiring that silently
 * sends documents to the wrong place would be caught.
 */
@Testcontainers
@ZeebeIntegration
final class DocumentStoreS3CompatibleIT {

  private static final String INDEX_PREFIX = "doc-s3-compat-it";

  @Container
  private static final LocalStackContainer LOCALSTACK =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.10"))
          .withServices("s3");

  @Container
  private static final GenericContainer<?> ELASTICSEARCH =
      TestSearchContainers.createDefaultElasticsearchContainer()
          .withStartupTimeout(Duration.ofMinutes(5));

  private static S3Client s3AdminClient;

  @TestZeebe(autoStart = false)
  private TestCamundaApplication testCamundaApplication;

  private CamundaClient camundaClient;
  private String bucketName;
  private final Set<String> setSystemProperties = new HashSet<>();

  @BeforeAll
  static void setupAdminClient() {
    s3AdminClient =
        S3Client.builder()
            .endpointOverride(LOCALSTACK.getEndpoint())
            .region(Region.of(LOCALSTACK.getRegion()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build();
  }

  @AfterEach
  void cleanup() {
    setSystemProperties.forEach(System::clearProperty);
    setSystemProperties.clear();
    CloseHelper.quietCloseAll(camundaClient);
    camundaClient = null;
  }

  @Test
  void shouldStoreDocumentInBackendWithChunkedEncodingDefault() {
    // given
    startCamundaWith(null);
    final byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);

    // when
    final var ref = uploadDocument(payload);

    // then
    final var head = s3AdminClient.headObject(b -> b.bucket(bucketName).key(ref.getDocumentId()));
    assertThat(head.contentLength()).isEqualTo((long) payload.length);
  }

  @Test
  void shouldStoreDocumentInBackendWithChunkedEncodingDisabled() {
    // given
    startCamundaWith(false);
    final byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);

    // when
    final var ref = uploadDocument(payload);

    // then
    final var head = s3AdminClient.headObject(b -> b.bucket(bucketName).key(ref.getDocumentId()));
    assertThat(head.contentLength()).isEqualTo((long) payload.length);
  }

  @Test
  void shouldFetchDocumentContentFromBackend() throws Exception {
    // given
    startCamundaWith(null);
    final byte[] payload = "fetch me".getBytes(StandardCharsets.UTF_8);
    final var ref = uploadDocument(payload);

    // when
    final byte[] fetched;
    try (final var stream =
        camundaClient
            .newDocumentContentGetRequest(ref.getDocumentId())
            .contentHash(ref.getContentHash())
            .send()
            .join()) {
      fetched = stream.readAllBytes();
    }

    // then
    assertThat(fetched).isEqualTo(payload);
  }

  @Test
  void shouldCreatePresignedLinkThatServesTheDocument() throws Exception {
    // given
    startCamundaWith(null);
    final byte[] payload = "link me".getBytes(StandardCharsets.UTF_8);
    final var ref = uploadDocument(payload);

    // when
    final var link =
        camundaClient
            .newCreateDocumentLinkCommand(ref.getDocumentId())
            .contentHash(ref.getContentHash())
            .timeToLive(Duration.ofMinutes(1))
            .send()
            .join();

    // then — the presigned URL points at the configured S3 endpoint, not real AWS
    final URI linkUri = URI.create(link.getUrl());
    final URI endpoint = LOCALSTACK.getEndpoint();
    assertThat(linkUri.getHost()).isEqualTo(endpoint.getHost());
    assertThat(linkUri.getPort()).isEqualTo(endpoint.getPort());

    // and — following the link returns the original document content
    try (final var httpClient = HttpClient.newHttpClient()) {
      final var response =
          httpClient.send(
              HttpRequest.newBuilder(linkUri).GET().build(), BodyHandlers.ofByteArray());
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).isEqualTo(payload);
    }
  }

  @Test
  void shouldDeleteDocumentFromBackend() {
    // given
    startCamundaWith(null);
    final var ref = uploadDocument("delete me".getBytes(StandardCharsets.UTF_8));
    // sanity: the object is in the bucket before delete
    s3AdminClient.headObject(b -> b.bucket(bucketName).key(ref.getDocumentId()));

    // when
    camundaClient.newDeleteDocumentCommand(ref.getDocumentId()).send().join();

    // then — the object is no longer in the bucket
    assertThatThrownBy(
            () -> s3AdminClient.headObject(b -> b.bucket(bucketName).key(ref.getDocumentId())))
        .isInstanceOf(NoSuchKeyException.class);
  }

  private DocumentReferenceResponse uploadDocument(final byte[] payload) {
    return camundaClient
        .newCreateDocumentCommand()
        .content(payload)
        .contentType("text/plain")
        .fileName("hello.txt")
        .send()
        .join();
  }

  private void startCamundaWith(final Boolean chunkedEncodingEnabled) {
    bucketName = "documents-" + UUID.randomUUID().toString().substring(0, 8);
    s3AdminClient.createBucket(b -> b.bucket(bucketName));

    setSystemProperty("DOCUMENT_DEFAULT_STORE_ID", "aws");
    setSystemProperty(
        "DOCUMENT_STORE_AWS_CLASS", "io.camunda.document.store.aws.AwsDocumentStoreProvider");
    setSystemProperty("DOCUMENT_STORE_AWS_BUCKET", bucketName);
    setSystemProperty("DOCUMENT_STORE_AWS_ENDPOINT", LOCALSTACK.getEndpoint().toString());
    if (chunkedEncodingEnabled != null) {
      setSystemProperty(
          "DOCUMENT_STORE_AWS_CHUNKED_ENCODING_ENABLED", chunkedEncodingEnabled.toString());
    }
    setSystemProperty("aws.accessKeyId", LOCALSTACK.getAccessKey());
    setSystemProperty("aws.secretAccessKey", LOCALSTACK.getSecretKey());
    setSystemProperty("aws.region", LOCALSTACK.getRegion());

    testCamundaApplication =
        new TestCamundaApplication()
            .withAuthenticationMethod(AuthenticationMethod.BASIC)
            .withUnauthenticatedAccess();
    new MultiDbConfigurator(testCamundaApplication)
        .configureElasticsearchSupportIncludingOldExporter(
            "http://" + ELASTICSEARCH.getHost() + ":" + ELASTICSEARCH.getFirstMappedPort(),
            INDEX_PREFIX);
    testCamundaApplication.start().awaitCompleteTopology();

    camundaClient = testCamundaApplication.newClientBuilder().build();
  }

  private void setSystemProperty(final String key, final String value) {
    System.setProperty(key, value);
    setSystemProperties.add(key);
  }
}
