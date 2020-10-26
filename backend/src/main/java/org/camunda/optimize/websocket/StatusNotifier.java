/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.status.StatusWithProgressResponseDto;
import org.camunda.optimize.service.importing.engine.service.ImportObserver;
import org.camunda.optimize.service.status.StatusCheckingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Session;
import java.io.IOException;
import java.util.Map;

public class StatusNotifier implements ImportObserver {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  private StatusCheckingService statusCheckingService;
  private ObjectMapper objectMapper;
  private Session session;

  private Map<String, Boolean> importStatusMap;

  public StatusNotifier(StatusCheckingService statusCheckingService, ObjectMapper objectMapper, Session session) {
    this.statusCheckingService = statusCheckingService;
    this.objectMapper = objectMapper;
    this.session = session;
    importStatusMap = statusCheckingService.getConnectionStatusWithProgress().getIsImporting();
    sendStatus();
  }

  @Override
  public synchronized void importInProgress(String engineAlias) {
    boolean sendUpdate = importStatusMap.containsKey(engineAlias) && !importStatusMap.get(engineAlias);
    importStatusMap.put(engineAlias, true);
    if (sendUpdate) {
      sendStatus();
    }
  }

  @Override
  public synchronized void importIsIdle(String engineAlias) {
    boolean sendUpdate = importStatusMap.containsKey(engineAlias) && importStatusMap.get(engineAlias);
    importStatusMap.put(engineAlias, false);
    if (sendUpdate) {
      sendStatus();
    }
  }

  private void sendStatus() {
    StatusWithProgressResponseDto result = new StatusWithProgressResponseDto();
    result.setConnectionStatus(statusCheckingService.getConnectionStatus());
    result.setIsImporting(importStatusMap);

    try {
      if (session.isOpen()) {
        session.getBasicRemote().sendText(objectMapper.writeValueAsString(result));
      } else {
        logger.debug("Could not write to websocket session [{}], because it already seems closed.", session.getId());
      }
    } catch (IOException e) {
      logger.error("can't write status to web socket", e);
    }
  }
}
