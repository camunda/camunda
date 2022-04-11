/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.status.StatusResponseDto;

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

    StatusResponseDto statusDto = objectMapper.readValue(message, StatusResponseDto.class);
    Boolean engineIsImporting = statusDto.getEngineStatus().get(DEFAULT_ENGINE_ALIAS).getIsImporting();

    assertThat(engineIsImporting).isNotNull();
    importStatusChanged |= importStatus != null && engineIsImporting != importStatus;
    importStatus = engineIsImporting;
    initialStatusReceivedLatch.countDown();
    receivedTwoUpdatesLatch.countDown();
  }

}
