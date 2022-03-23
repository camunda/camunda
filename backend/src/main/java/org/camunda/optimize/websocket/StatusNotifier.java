/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.status.EngineStatusDto;
import org.camunda.optimize.dto.optimize.query.status.StatusResponseDto;
import org.camunda.optimize.service.importing.engine.service.ImportObserver;
import org.camunda.optimize.service.status.StatusCheckingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Session;
import java.io.IOException;
import java.util.Map;

public class StatusNotifier implements ImportObserver {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final StatusCheckingService statusCheckingService;
  private final ObjectMapper objectMapper;
  private final Session session;

  private final Map<String, EngineStatusDto> engineStatusMap;

  public StatusNotifier(StatusCheckingService statusCheckingService, ObjectMapper objectMapper, Session session) {
    this.statusCheckingService = statusCheckingService;
    this.objectMapper = objectMapper;
    this.session = session;
    engineStatusMap = statusCheckingService.getStatusResponse().getEngineStatus();
    sendStatus();
  }

  @Override
  public synchronized void importInProgress(String engineAlias) {
    boolean containsKey = engineStatusMap.containsKey(engineAlias);
    boolean sendUpdate = containsKey && !engineStatusMap.get(engineAlias).getIsImporting();
    EngineStatusDto engineStatus = new EngineStatusDto();
    if (containsKey) {
      engineStatus.setIsConnected(engineStatusMap.get(engineAlias).getIsConnected());
    } else {
      engineStatus.setIsConnected(false);
    }
    engineStatus.setIsImporting(true);
    engineStatusMap.put(engineAlias, engineStatus);
    if (sendUpdate) {
      sendStatus();
    }
  }

  @Override
  public synchronized void importIsIdle(String engineAlias) {
    boolean containsKey = engineStatusMap.containsKey(engineAlias);
    boolean sendUpdate = containsKey && engineStatusMap.get(engineAlias).getIsImporting();
    EngineStatusDto engineStatus = new EngineStatusDto();
    if (containsKey) {
      engineStatus.setIsConnected(engineStatusMap.get(engineAlias).getIsConnected());
    } else {
      engineStatus.setIsConnected(false);
    }
    engineStatus.setIsImporting(false);
    engineStatusMap.put(engineAlias, engineStatus);
    if (sendUpdate) {
      sendStatus();
    }
  }

  private void sendStatus() {
    StatusResponseDto result = statusCheckingService.getCachedStatusResponse();
    try {
      if (session.isOpen()) {
        session.getBasicRemote().sendText(objectMapper.writeValueAsString(result));
      } else {
        logger.debug("Could not write to websocket session [{}], because it already seems closed.", session.getId());
      }
    } catch (IOException e) {
      logger.warn("can't write status to web socket");
      logger.debug("Exception when writing status", e);
    }
  }
}
