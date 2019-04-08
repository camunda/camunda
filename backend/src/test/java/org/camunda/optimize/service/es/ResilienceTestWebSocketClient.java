/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.status.StatusWithProgressDto;
import org.junit.runners.model.TestTimedOutException;

import javax.websocket.ClientEndpoint;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import java.util.concurrent.TimeUnit;

import static org.camunda.optimize.service.es.ResilienceTest.TIMEOUT_CONNECTION_STATUS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@ClientEndpoint
public class ResilienceTestWebSocketClient {

  private long startTime;

  public void setStartTime(long startTime) {
    this.startTime = startTime;
  }

  @OnMessage
  public void onText(String message, Session session) throws Exception {
    System.out.println("Message received from server:" + message);
    ObjectMapper objectMapper = new ObjectMapper();
    StatusWithProgressDto dto = objectMapper.readValue(message, StatusWithProgressDto.class);
    assertThat(dto.getConnectionStatus().isConnectedToElasticsearch(), is(notNullValue()));

    long requestDuration = System.currentTimeMillis() - startTime;

    if (requestDuration > TIMEOUT_CONNECTION_STATUS) {
      throw new TestTimedOutException(TIMEOUT_CONNECTION_STATUS, TimeUnit.MILLISECONDS);
    }
  }
}
