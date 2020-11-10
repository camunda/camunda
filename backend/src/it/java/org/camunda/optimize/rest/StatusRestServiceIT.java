/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.status.StatusWithProgressResponseDto;
import org.camunda.optimize.service.util.importing.EngineConstants;
import org.camunda.optimize.test.it.extension.ErrorResponseMock;
import org.camunda.optimize.test.it.extension.MockServerUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;

import java.util.Map;
import java.util.stream.Stream;

import static javax.ws.rs.HttpMethod.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.mockserver.model.HttpRequest.request;

public class StatusRestServiceIT extends AbstractIT {

  @Test
  public void getConnectedStatus() {
    final StatusWithProgressResponseDto statusWithProgressDto = statusClient.getStatus();

    assertThat(statusWithProgressDto.getConnectionStatus().isConnectedToElasticsearch()).isTrue();
    assertThat(statusWithProgressDto.getConnectionStatus().getEngineConnections()).hasSize(1);
    assertThat(statusWithProgressDto.getConnectionStatus().getEngineConnections().get(DEFAULT_ENGINE_ALIAS)).isTrue();
  }

  @Test
  public void getImportStatus() {
    final StatusWithProgressResponseDto statusWithProgressDto = statusClient.getStatus();

    assertThat(statusWithProgressDto.getIsImporting().keySet()).contains(DEFAULT_ENGINE_ALIAS);
  }

  @Test
  public void importStatusIsTrueWhenImporting() {
    // given
    importAllEngineEntitiesFromScratch();

    // when
    final StatusWithProgressResponseDto status = statusClient.getStatus();

    // then
    final Map<String, Boolean> isImportingMap = status.getIsImporting();
    assertThat(isImportingMap).isNotNull();
    assertThat(isImportingMap.get(DEFAULT_ENGINE_ALIAS)).isTrue();
  }

  @Test
  public void importStatusIsFalseWhenNotImporting() {
    // when
    final StatusWithProgressResponseDto status = statusClient.getStatus();

    // then
    final Map<String, Boolean> isImportingMap = status.getIsImporting();
    assertThat(isImportingMap).isNotNull();
    assertThat(isImportingMap.get(DEFAULT_ENGINE_ALIAS)).isFalse();
  }

  @ParameterizedTest
  @MethodSource("engineErrors")
  public void connectionStatusFalseWhenVersionEndpointFails(ErrorResponseMock mockedResponse) {
    // given
    final ClientAndServer engineMockServer = useAndGetEngineMockServer();
    final HttpRequest request = request()
      .withPath(".*" + EngineConstants.VERSION_ENDPOINT)
      .withMethod(GET);
    mockedResponse.mock(request, Times.once(), engineMockServer);

    // when
    final StatusWithProgressResponseDto status = statusClient.getStatus();

    // then
    final Map<String, Boolean> connectionStatusMap = status.getConnectionStatus().getEngineConnections();
    assertThat(connectionStatusMap).isNotNull();
    assertThat(connectionStatusMap.get(DEFAULT_ENGINE_ALIAS)).isFalse();
  }

  private static Stream<ErrorResponseMock> engineErrors() {
    return MockServerUtil.engineMockedErrorResponses();
  }

}
