/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.status.EngineStatusDto;
import org.camunda.optimize.dto.optimize.query.status.StatusResponseDto;
import org.camunda.optimize.service.util.importing.EngineConstants;
import org.camunda.optimize.test.it.extension.ErrorResponseMock;
import org.camunda.optimize.test.it.extension.MockServerUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.VerificationTimes;

import java.util.Map;
import java.util.stream.Stream;

import static javax.ws.rs.HttpMethod.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.mockserver.model.HttpRequest.request;

public class StatusRestServiceIT extends AbstractIT {

  @Test
  public void getConnectedStatus() {
    // when
    final StatusResponseDto statusWithProgressDto = statusClient.getStatus();

    // then
    assertThat(statusWithProgressDto.isConnectedToElasticsearch()).isTrue();
    assertThat(statusWithProgressDto.getEngineStatus()).hasSize(1);
    assertThat(statusWithProgressDto.getEngineStatus().get(DEFAULT_ENGINE_ALIAS).getIsConnected()).isTrue();
  }

  @Test
  public void importStatusIsTrueWhenImporting() {
    // given
    importAllEngineEntitiesFromScratch();

    // when
    final StatusResponseDto status = statusClient.getStatus();

    // then
    final EngineStatusDto engineConnection = status.getEngineStatus().get(DEFAULT_ENGINE_ALIAS);
    assertThat(engineConnection.getIsImporting()).isTrue();
  }

  @Test
  public void importStatusIsFalseWhenNotImporting() {
    // when
    final StatusResponseDto status = statusClient.getStatus();

    // then
    final EngineStatusDto engineConnection = status.getEngineStatus().get(DEFAULT_ENGINE_ALIAS);
    assertThat(engineConnection.getIsImporting()).isFalse();
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
    final StatusResponseDto status = statusClient.getStatus();

    // then
    final Map<String, EngineStatusDto> connectionStatusMap = status.getEngineStatus();
    assertThat(connectionStatusMap).isNotNull();
    assertThat(connectionStatusMap.get(DEFAULT_ENGINE_ALIAS)).extracting(
      EngineStatusDto::getIsConnected,
      EngineStatusDto::getIsImporting
    ).containsExactly(false, false);
  }

  @Test
  public void getConnectedStatusSkipsCache() {
    // given
    final ClientAndServer engineMockServer = useAndGetEngineMockServer();
    final HttpRequest engineVersionRequestMatcher = request()
      .withPath(engineIntegrationExtension.getEnginePath() + "/version");

    // when
    statusClient.getStatus();
    statusClient.getStatus();

    // then both requests make actual requests to the engine
    engineMockServer.verify(engineVersionRequestMatcher, VerificationTimes.exactly(2));
  }

  private static Stream<ErrorResponseMock> engineErrors() {
    return MockServerUtil.engineMockedErrorResponses();
  }

}
