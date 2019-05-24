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
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import static org.camunda.optimize.websocket.StatusWebSocketIT.ENGINE_ALIAS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Client class to test Web Socket implementation of status
 * report is working. This class will assert 2 properties:
 * <p>
 * 1. import status has changed
 * 2. more then one message is received
 */
@ClientEndpoint
@Slf4j
public class AssertHasChangedStatusClientSocket {

  @Getter
  private CountDownLatch initialStatusReceivedLatch = new CountDownLatch(1);

  @Getter
  private CountDownLatch receivedTwoUpdatesLatch = new CountDownLatch(2);

  private Boolean importStatus = null;
  private boolean importStatusChanged = false;
  private ObjectMapper objectMapper = new ObjectMapper();

  @OnMessage
  public void onText(String message, Session session) throws Exception {
    log.info("Message received from server:" + message);

    StatusWithProgressDto dto = objectMapper.readValue(message, StatusWithProgressDto.class);

    assertThat(dto.getIsImporting(), is(notNullValue()));
    initialStatusReceivedLatch.countDown();

    importStatusChanged |= importStatus != null && dto.getIsImporting().get(ENGINE_ALIAS) != importStatus;
    importStatus = dto.getIsImporting().get(ENGINE_ALIAS);
    receivedTwoUpdatesLatch.countDown();
  }

  public Optional<Boolean> getImportStatus() {
    return Optional.ofNullable(importStatus);
  }

  public boolean isImportStatusChanged() {
    return importStatusChanged;
  }
}
