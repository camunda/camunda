/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.cluster;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.UserEntity;
import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.es.clients.ElasticsearchSearchClient;
import io.camunda.zeebe.util.CloseableSilently;
import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public abstract class AbstractTestWebappClient<SELF extends CloseableSilently>
    implements CloseableSilently {

  protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  protected final URI endpoint;
  protected final HttpClient httpClient;
  protected final ElasticsearchClient elasticsearchClient;
  protected final CamundaSearchClient searchClient;
  protected final PasswordEncoder passwordEncoder;
  private final List<CloseableSilently> closables = new ArrayList<>();

  protected AbstractTestWebappClient(
      final URI endpoint,
      final HttpClient httpClient,
      final ElasticsearchClient elasticsearchClient) {
    this.endpoint = endpoint;
    this.httpClient = httpClient;
    this.elasticsearchClient = elasticsearchClient;
    searchClient = new ElasticsearchSearchClient(elasticsearchClient);
    passwordEncoder = new BCryptPasswordEncoder();
  }

  public SELF withAuthentication(final String username, final String password) {
    final HttpClient httpClient =
        HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
    sendRequest(
        httpClient,
        "POST",
        String.format("%sapi/login?username=%s&password=%s", endpoint, username, password),
        null);
    final var testClient = create(httpClient);
    closables.add(testClient);
    return testClient;
  }

  protected abstract SELF create(HttpClient httpClient);

  protected HttpResponse<String> sendRequest(
      final String method, final String path, final String body) {
    return sendRequest(httpClient, method, path, body);
  }

  private HttpResponse<String> sendRequest(
      final HttpClient httpClient, final String method, final String path, final String body) {
    try {
      final var requestBody = Optional.ofNullable(body).orElse("{}");
      final var requestBuilder =
          HttpRequest.newBuilder()
              .uri(new URI(path))
              .header("content-type", "application/json")
              .method(method, HttpRequest.BodyPublishers.ofString(requestBody));

      final var request = requestBuilder.build();

      return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (final Exception e) {
      throw new RuntimeException("Failed to send request", e);
    }
  }

  protected void createUser(final String userIndex, final String username, final String password) {
    final String passwordEncoded = passwordEncoder.encode(password);
    final UserEntity user =
        new UserEntity()
            .setId(username)
            .setUserId(username)
            .setPassword(passwordEncoded)
            .setDisplayName(username)
            .setRoles(List.of("OWNER"));
    final var request =
        new IndexRequest.Builder<UserEntity>()
            .index(userIndex)
            .id(user.getId())
            .document(user)
            .build();
    try {
      elasticsearchClient.index(request);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected static ElasticsearchClient createElasticsearchClient(
      final String elasticsearchUrl, final String username, final String password) {
    final var config = new ConnectConfiguration();
    config.setUrl(elasticsearchUrl);
    config.setUsername(username);
    config.setPassword(password);
    return new ElasticsearchConnector(config).createClient();
  }

  @Override
  public void close() {
    httpClient.close();
    searchClient.close();
    closables.forEach(CloseableSilently::close);
  }

  public String getCookie() {
    final var cookieStore = ((CookieManager) httpClient.cookieHandler().get()).getCookieStore();
    final List<HttpCookie> cookies = cookieStore.get(endpoint);

    return cookies.stream()
        .map(cookie -> cookie.getName() + "=" + cookie.getValue())
        .collect(Collectors.joining("; "));
  }
}
