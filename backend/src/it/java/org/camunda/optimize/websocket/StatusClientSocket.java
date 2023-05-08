/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.status.EngineStatusDto;
import org.camunda.optimize.dto.optimize.query.status.StatusResponseDto;

import javax.websocket.ClientEndpoint;
import javax.websocket.OnMessage;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;

/**
 * Client class to test Web Socket implementation of status
 * report is working. This class provides two latches:
 * <p>
 * 1. initialStatusReceivedLatch is count down when an initial message is received
 * 2. importingStatusReceivedLatch is count down when a message with isImporting = true is received
 */
@ClientEndpoint
@Slf4j
public class StatusClientSocket {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Getter
  private final CountDownLatch initialStatusReceivedLatch = new CountDownLatch(1);

  @Getter
  private final CountDownLatch importingStatusReceivedLatch = new CountDownLatch(1);


  @OnMessage
  public void onText(String message) throws Exception {
    log.info("Message received from server:" + message);

    StatusResponseDto dto = objectMapper.readValue(message, StatusResponseDto.class);

    assertThat(dto.getEngineStatus().get(DEFAULT_ENGINE_ALIAS)).isNotNull();
    initialStatusReceivedLatch.countDown();

    Map<String, EngineStatusDto> engineConnections = dto.getEngineStatus();
    if (engineConnections.get(DEFAULT_ENGINE_ALIAS).getIsImporting()) {
      importingStatusReceivedLatch.countDown();
    }
  }

}
