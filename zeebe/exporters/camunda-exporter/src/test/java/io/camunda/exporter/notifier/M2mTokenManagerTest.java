/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.notifier;

import static io.camunda.exporter.notifier.M2mTokenManager.FIELD_NAME_ACCESS_TOKEN;
import static io.camunda.exporter.notifier.M2mTokenManager.FIELD_NAME_AUDIENCE;
import static io.camunda.exporter.notifier.M2mTokenManager.FIELD_NAME_CLIENT_ID;
import static io.camunda.exporter.notifier.M2mTokenManager.FIELD_NAME_CLIENT_SECRET;
import static io.camunda.exporter.notifier.M2mTokenManager.FIELD_NAME_GRANT_TYPE;
import static io.camunda.exporter.notifier.M2mTokenManager.GRANT_TYPE_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import io.camunda.exporter.config.ExporterConfiguration.IncidentNotifierConfiguration;
import io.camunda.search.test.utils.TestObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.quality.Strictness;

class M2mTokenManagerTest {
  protected static final String AUTH0_DOMAIN = "auth0.domain";
  protected static final String M2M_CLIENT_ID = "clientId";
  protected static final String M2M_CLIENT_SECRET = "clientSecret";
  protected static final String M2M_AUDIENCE = "audience";
  private static final ObjectMapper MAPPER = TestObjectMapper.objectMapper();
  private final String mockJwtToken =
      JWT.create()
          .withExpiresAt(new Date(Instant.now().plus(10, ChronoUnit.MINUTES).toEpochMilli()))
          .sign(Algorithm.HMAC256("secret"));
  private final HttpClient httpClient = mock(HttpClient.class);
  private M2mTokenManager m2mTokenManager;

  @BeforeEach
  public void setup() {
    final IncidentNotifierConfiguration configuration = new IncidentNotifierConfiguration();
    configuration.setAuth0Domain(AUTH0_DOMAIN);
    configuration.setM2mClientId(M2M_CLIENT_ID);
    configuration.setM2mClientSecret(M2M_CLIENT_SECRET);
    configuration.setM2mAudience(M2M_AUDIENCE);

    m2mTokenManager =
        new M2mTokenManager(configuration, httpClient, TestObjectMapper.objectMapper());
  }

  @AfterEach
  public void cleanup() {
    m2mTokenManager.clearCache();
  }

  @Test
  public void shouldReturnCachedTokenOnSubsequentCalls() throws IOException, InterruptedException {
    // given
    final HttpResponse response =
        mock(
            HttpResponse.class,
            withSettings().defaultAnswer(RETURNS_DEEP_STUBS).strictness(Strictness.LENIENT));
    when(response.body())
        .thenReturn(MAPPER.writeValueAsString(Map.of(FIELD_NAME_ACCESS_TOKEN, mockJwtToken)));
    when(response.statusCode()).thenReturn(200);

    given(httpClient.send(any(), any())).willReturn(response);

    // when requesting the token for the 1st time
    String token = m2mTokenManager.getToken();

    // then
    assertThat(token).isEqualTo(mockJwtToken);
    assertAuth0IsRequested(1);

    // when requesting the token for the 2nd time
    token = m2mTokenManager.getToken();

    // then
    assertThat(token).isEqualTo(mockJwtToken);
    // no request to Auth0 is sent
    verifyNoMoreInteractions(httpClient);
  }

  @Test
  public void shouldRequestNewTokenWhenForceUpdate() throws IOException, InterruptedException {
    // given
    final HttpResponse response =
        mock(
            HttpResponse.class,
            withSettings().defaultAnswer(RETURNS_DEEP_STUBS).strictness(Strictness.LENIENT));
    when(response.body())
        .thenReturn(MAPPER.writeValueAsString(Map.of(FIELD_NAME_ACCESS_TOKEN, mockJwtToken)));

    when(response.statusCode()).thenReturn(200);

    given(httpClient.send(any(), any())).willReturn(response);

    // when
    // requesting for the 1st time
    m2mTokenManager.getToken();
    // requesting with forceUpdate = true
    String token = m2mTokenManager.getToken(true);

    // then
    assertThat(token).isEqualTo(mockJwtToken);
    // rest call was sent twice
    assertAuth0IsRequested(2);

    // when requesting with forceUpdate = false
    token = m2mTokenManager.getToken(false);

    // then
    assertThat(token).isEqualTo(mockJwtToken);
    // no request to Auth0 is sent
    verifyNoMoreInteractions(httpClient);
  }

  @Test
  public void shouldRequestNewTokenIfCachedTokenIsExpired()
      throws IOException, InterruptedException {
    // given
    final String mockExpiredJwtToken =
        JWT.create()
            .withExpiresAt(new Date(Instant.now().minus(10, ChronoUnit.MINUTES).toEpochMilli()))
            .sign(Algorithm.HMAC256("secret"));

    final HttpResponse response =
        mock(
            HttpResponse.class,
            withSettings().defaultAnswer(RETURNS_DEEP_STUBS).strictness(Strictness.LENIENT));
    when(response.body())
        .thenReturn(MAPPER.writeValueAsString(Map.of(FIELD_NAME_ACCESS_TOKEN, mockExpiredJwtToken)))
        .thenReturn(MAPPER.writeValueAsString(Map.of(FIELD_NAME_ACCESS_TOKEN, mockJwtToken)));

    when(response.statusCode()).thenReturn(200);

    given(httpClient.send(any(), any())).willReturn(response, response);

    // cache the expired token
    m2mTokenManager.getToken();
    clearInvocations(httpClient);

    // when asking for token again
    final String token = m2mTokenManager.getToken();

    // then
    assertAuth0IsRequested(1);
    assertThat(token).isEqualTo(mockJwtToken);
  }

  private void assertAuth0IsRequested(final int times) throws IOException, InterruptedException {
    // assert request to Auth0
    final ArgumentCaptor<HttpRequest> tokenRequestCaptor =
        ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpClient, times(times)).send(tokenRequestCaptor.capture(), any());

    final HttpRequest request = tokenRequestCaptor.getValue();
    assertThat(request.uri().toString())
        .isEqualTo(String.format("https://%s/oauth/token", AUTH0_DOMAIN));

    final String body = extractBody(request.bodyPublisher().orElseThrow());
    final DocumentContext jsonContext = JsonPath.parse(body);
    final Map<String, Object> value = jsonContext.read("$");
    assertThat(value.get(FIELD_NAME_GRANT_TYPE)).isEqualTo(GRANT_TYPE_VALUE);
    assertThat(value.get(FIELD_NAME_CLIENT_ID)).isEqualTo(M2M_CLIENT_ID);
    assertThat(value.get(FIELD_NAME_CLIENT_SECRET)).isEqualTo(M2M_CLIENT_SECRET);
    assertThat(value.get(FIELD_NAME_AUDIENCE)).isEqualTo(M2M_AUDIENCE);
  }

  private String extractBody(final HttpRequest.BodyPublisher bodyPublisher)
      throws IOException, InterruptedException {
    final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    final CountDownLatch latch = new CountDownLatch(1);

    bodyPublisher.subscribe(
        new Subscriber<>() {
          @Override
          public void onSubscribe(final Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
          }

          @Override
          public void onNext(final ByteBuffer item) {
            outputStream.write(item.array(), item.position(), item.remaining());
          }

          @Override
          public void onError(final Throwable throwable) {
            latch.countDown();
            throw new RuntimeException(throwable);
          }

          @Override
          public void onComplete() {
            latch.countDown();
          }
        });

    latch.await(); // Wait for the body to be fully written
    return outputStream.toString(StandardCharsets.UTF_8);
  }
}
