/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.status.StatusWithProgressResponseDto;

import javax.websocket.ClientEndpoint;
import javax.websocket.OnMessage;
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
  private ObjectMapper objectMapper = new ObjectMapper();

  @Getter
  private CountDownLatch initialStatusReceivedLatch = new CountDownLatch(1);

  @Getter
  private CountDownLatch importingStatusReceivedLatch = new CountDownLatch(1);


  @OnMessage
  public void onText(String message) throws Exception {
    log.info("Message received from server:" + message);

    StatusWithProgressResponseDto dto = objectMapper.readValue(message, StatusWithProgressResponseDto.class);

    assertThat(dto.getIsImporting()).isNotNull();
    initialStatusReceivedLatch.countDown();

    if (dto.getIsImporting().get(DEFAULT_ENGINE_ALIAS)) {
      importingStatusReceivedLatch.countDown();
    }
  }

}
