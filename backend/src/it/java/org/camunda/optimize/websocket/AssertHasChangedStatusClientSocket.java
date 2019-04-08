/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.status.StatusWithProgressDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *
 * 1. import status has changed
 * 2. more then one message is received
 */
@ClientEndpoint
public class AssertHasChangedStatusClientSocket {

  private static final Logger logger = LoggerFactory.getLogger(AssertHasChangedStatusClientSocket.class);

  private CountDownLatch receivedTwoUpdatesLatch = new CountDownLatch(2);

  private Boolean importStatus = null;
  private boolean importStatusChanged = false;
  private ObjectMapper objectMapper = new ObjectMapper();

  @OnMessage
  public void onText(String message, Session session) throws Exception {
    logger.info("Message received from server:" + message);

    StatusWithProgressDto dto = objectMapper.readValue(message, StatusWithProgressDto.class);

    assertThat(dto.getIsImporting(), is(notNullValue()));
    importStatusChanged |= importStatus != null && dto.getIsImporting().get(ENGINE_ALIAS) != importStatus;
    importStatus = dto.getIsImporting().get(ENGINE_ALIAS);
    receivedTwoUpdatesLatch.countDown();
  }

  public CountDownLatch getReceivedTwoUpdatesLatch() {
    return receivedTwoUpdatesLatch;
  }

  public Optional<Boolean> getImportStatus() {
    return Optional.ofNullable(importStatus);
  }

  public boolean isImportStatusChanged() {
    return importStatusChanged;
  }
}
