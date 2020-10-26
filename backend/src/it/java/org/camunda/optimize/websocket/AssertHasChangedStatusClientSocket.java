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
 * report is working. This class will assert 2 properties:
 * <p>
 * 1. import status has changed
 * 2. more then one message is received
 */
@ClientEndpoint
@Slf4j
@Getter
public class AssertHasChangedStatusClientSocket {
  
  private CountDownLatch initialStatusReceivedLatch = new CountDownLatch(1);
  private CountDownLatch receivedTwoUpdatesLatch = new CountDownLatch(2);
  private boolean importStatusChanged = false;
  private Boolean importStatus = null;
  private ObjectMapper objectMapper = new ObjectMapper();

  @OnMessage
  public void onText(String message) throws Exception {
    log.info("Message received from server:" + message);

    StatusWithProgressResponseDto statusDto = objectMapper.readValue(message, StatusWithProgressResponseDto.class);

    assertThat(statusDto.getIsImporting()).isNotNull();
    importStatusChanged |= importStatus != null && statusDto.getIsImporting().get(DEFAULT_ENGINE_ALIAS) != importStatus;
    importStatus = statusDto.getIsImporting().get(DEFAULT_ENGINE_ALIAS);
    initialStatusReceivedLatch.countDown();
    receivedTwoUpdatesLatch.countDown();
  }

}
