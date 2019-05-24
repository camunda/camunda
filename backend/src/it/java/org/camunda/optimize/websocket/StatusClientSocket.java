/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.status.StatusWithProgressDto;

import javax.websocket.ClientEndpoint;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import java.util.concurrent.CountDownLatch;

import static org.camunda.optimize.websocket.StatusWebSocketIT.ENGINE_ALIAS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

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
  public void onText(String message, Session session) throws Exception {
    log.info("Message received from server:" + message);

    StatusWithProgressDto dto = objectMapper.readValue(message, StatusWithProgressDto.class);

    assertThat(dto.getIsImporting(), is(notNullValue()));
    initialStatusReceivedLatch.countDown();

    if (dto.getIsImporting().get(ENGINE_ALIAS)) {
      importingStatusReceivedLatch.countDown();
    }
  }

}
