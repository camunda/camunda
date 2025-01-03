/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.cluster;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.management.backups.TakeBackupHistoryRequest;
import io.camunda.management.backups.TakeBackupHistoryResponse;
import io.camunda.webapps.backup.GetBackupStateResponseDto;
import io.camunda.zeebe.util.Either;
import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

public class TestRestManagementClient {

  private static final ObjectMapper OBJECT_MAPPER =
      JsonMapper.builder().addModule(new JavaTimeModule()).build();

  private final URI endpoint;
  private final HttpClient httpClient;

  public TestRestManagementClient(
      final URI endpoint, final String username, final String password) {
    this(
        endpoint,
        HttpClient.newBuilder()
            .authenticator(
                new Authenticator() {
                  @Override
                  protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password.toCharArray());
                  }
                })
            .build());
  }

  public TestRestManagementClient(final URI endpoint) {
    this(endpoint, HttpClient.newHttpClient());
  }

  private TestRestManagementClient(final URI endpoint, final HttpClient httpClient) {
    this.endpoint = endpoint;
    this.httpClient = httpClient;
  }

  public Either<Exception, TakeBackupHistoryResponse> takeBackup(final long id) {
    try {
      final var req = new TakeBackupHistoryRequest(id);
      final var httpReq =
          HttpRequest.newBuilder(new URI(String.format("%s/backupHistory", endpoint)))
              .header("Content-Type", "application/json")
              .POST(BodyPublishers.ofByteArray(OBJECT_MAPPER.writeValueAsBytes(req)))
              .build();
      return sendRequest(httpReq)
          .map(
              resp -> {
                try {
                  return OBJECT_MAPPER.readValue(resp.body(), TakeBackupHistoryResponse.class);
                } catch (final JsonProcessingException e) {
                  throw new RuntimeException(e);
                }
              });
    } catch (final Exception e) {
      return Either.left(e);
    }
  }

  public Either<Exception, GetBackupStateResponseDto> getBackup(final long id) {
    try {
      final var httpReq =
          HttpRequest.newBuilder(new URI(String.format("%s/backupHistory/%d", endpoint, id)))
              .header("Content-Type", "application/json")
              .GET()
              .build();
      return sendRequest(httpReq)
          .map(
              resp -> {
                try {
                  return OBJECT_MAPPER.readValue(resp.body(), GetBackupStateResponseDto.class);
                } catch (final JsonProcessingException e) {
                  throw new RuntimeException(e);
                }
              });
    } catch (final Exception e) {
      return Either.left(e);
    }
  }

  private Either<Exception, HttpResponse<String>> sendRequest(final HttpRequest request) {
    try {
      return Either.right(httpClient.send(request, BodyHandlers.ofString()));
    } catch (final IOException | InterruptedException e) {
      return Either.left(e);
    }
  }
}
