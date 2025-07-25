/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.acm19.aws.interceptor.http.AwsRequestSigningApacheInterceptor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.ProtocolVersion;
import org.apache.http.RequestLine;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;

class AwsRequestSigningApacheInterceptorTest {
  private static final String AWS_SERVICE_NAME = "servicename";
  private static final String AWS_REGION = "eu-west-1";
  private static final String AWS_SECRET_ACCESS_KEY = "awsSecretAcessKey";
  private static final String AWS_ACCESS_KEY_ID = "awsAccessKeyId";
  private static final HttpCoreContext CONTEXT = new HttpCoreContext();
  private static AwsRequestSigningApacheInterceptor interceptor;

  @BeforeAll
  static void beforeAll() {
    final var mockCredentialsProvider = mock(AwsCredentialsProvider.class);
    final var mockAwsCredentials = mock(AwsCredentials.class);
    when(mockCredentialsProvider.resolveCredentials()).thenReturn(mockAwsCredentials);
    when(mockAwsCredentials.accessKeyId()).thenReturn(AWS_ACCESS_KEY_ID);
    when(mockAwsCredentials.secretAccessKey()).thenReturn(AWS_SECRET_ACCESS_KEY);
    StaticCredentialsProvider.create(AnonymousCredentialsProvider.create().resolveCredentials());
    final var signer = AwsV4HttpSigner.create();
    interceptor =
        new AwsRequestSigningApacheInterceptor(
            AWS_SERVICE_NAME, signer, mockCredentialsProvider, AWS_REGION);
    CONTEXT.setTargetHost(HttpHost.create("localhost"));
  }

  @Test
  void shouldInterceptPutRequest() throws Exception {
    // given
    final HttpEntityEnclosingRequest request =
        new BasicHttpEntityEnclosingRequest(new MockRequestLine("PUT", "/index_template/foo"));
    final var content = "{\"foo\": \"bar\"}";
    final var entity = new NStringEntity(content, ContentType.APPLICATION_JSON);
    request.setEntity(entity);

    // when
    interceptor.process(request, CONTEXT);

    // then
    assertThat(request.getRequestLine().getMethod()).isEqualTo("PUT");
    assertAwsHeaders(request);
    final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    request.getEntity().writeTo(outputStream);
    assertThat(outputStream.toString()).isEqualTo(content);
  }

  @Test
  void shouldInterceptPostRequest() throws Exception {
    // given
    final HttpEntityEnclosingRequest request =
        new BasicHttpEntityEnclosingRequest(new MockRequestLine("POST", "/_bulk"));
    final var content = "{\"foo\": \"bar\"}";
    final var entity = new NStringEntity(content, ContentType.APPLICATION_JSON);
    request.setEntity(entity);

    // when
    interceptor.process(request, CONTEXT);

    // then
    assertThat(request.getRequestLine().getMethod()).isEqualTo("POST");
    final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    request.getEntity().writeTo(outputStream);
    assertThat(outputStream.toString()).isEqualTo(content);
    assertAwsHeaders(request);
  }

  @Test
  void shouldCreateRepeatableEntity() throws Exception {
    final HttpEntityEnclosingRequest request =
        new BasicHttpEntityEnclosingRequest(new MockRequestLine("POST", "/"));

    final String payload = "{\"test\": \"val\"}";
    request.setEntity(new StringEntity(payload));
    final HttpCoreContext context = new HttpCoreContext();
    context.setTargetHost(HttpHost.create("localhost"));
    interceptor.process(request, context);

    assertThat(request.getEntity().isRepeatable()).isTrue();
  }

  @Test
  void shouldThrowBadRequest() {
    final HttpRequest badRequest = new BasicHttpRequest("GET", "?#!@*%");
    assertThatExceptionOfType(IOException.class)
        .isThrownBy(() -> interceptor.process(badRequest, new BasicHttpContext()));
  }

  private static void assertAwsHeaders(final HttpEntityEnclosingRequest request) {
    final var authorizationHeader = request.getFirstHeader("Authorization");

    final var dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
    final var currentDate = LocalDate.now().format(dateFormatter);
    final var credentialsString =
        "Credential="
            + AWS_ACCESS_KEY_ID
            + "/"
            + currentDate
            + "/"
            + AWS_REGION
            + "/"
            + AWS_SERVICE_NAME
            + "/aws4_request";

    assertThat(authorizationHeader.getValue())
        .describedAs("Starts with algorithm")
        .startsWith("AWS4-HMAC-SHA256")
        .describedAs("Contains Credentials")
        .contains(credentialsString)
        .describedAs("Contains SignedHeaders")
        .contains("SignedHeaders=host;x-amz-content-sha256;x-amz-date")
        .describedAs("Contains Signature")
        // As the signature is a random hash we can't verify the value
        .contains("Signature=");
  }

  private static class MockRequestLine implements RequestLine {
    private final String uri;
    private final String method;

    MockRequestLine(final String method, final String uri) {
      this.method = method;
      this.uri = uri;
    }

    @Override
    public String getMethod() {
      return method;
    }

    @Override
    public ProtocolVersion getProtocolVersion() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getUri() {
      return uri;
    }
  }
}
