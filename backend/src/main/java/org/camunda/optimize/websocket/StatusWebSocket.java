/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.service.engine.importing.EngineImportSchedulerFactory;
import org.camunda.optimize.service.status.StatusCheckingService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.server.standard.SpringConfigurator;

import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


@ServerEndpoint(value = "/ws/status", configurator = SpringConfigurator.class)
public class StatusWebSocket {

  @Autowired
  private StatusCheckingService statusCheckingService;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private EngineImportSchedulerFactory engineImportSchedulerFactory;


  private Map<String, StatusNotifier> statusReportJobs = new HashMap<>();
  private final Logger logger = LoggerFactory.getLogger(getClass());


  @OnOpen
  public void onOpen(Session session) {
    if (statusReportJobs.size() <= configurationService.getMaxStatusConnections()) {
      StatusNotifier job = new StatusNotifier(
        statusCheckingService,
        objectMapper,
        session
      );
      statusReportJobs.put(session.getId(), job);
      engineImportSchedulerFactory.getImportSchedulers().forEach(s -> s.subscribe(job));
      logger.debug("starting to report status for session [{}]", session.getId());
    } else {
      logger.debug("cannot create status report job for [{}], max connections exceeded",session.getId());
      try {
        session.close();
      } catch (IOException e) {
        logger.error("can't close status report web socket session");
      }
    }

  }

  @OnClose
  public void onClose(CloseReason reason, Session session) {
    logger.debug("stopping to report status for session [{}]",session.getId());
    removeSession(session);
  }

  private void removeSession(Session session) {
    if (statusReportJobs.containsKey(session.getId())) {
      StatusNotifier job = statusReportJobs.remove(session.getId());
      engineImportSchedulerFactory.getImportSchedulers().forEach(s -> s.unsubscribe(job));
    }
  }

  @OnError
  public void onError(Throwable t, Session session) {
    String message = "Web socket connection terminated prematurely!";
    if (logger.isWarnEnabled()) {
      logger.warn(message);
    } else if (logger.isDebugEnabled()) {
      logger.debug(message, t);
    }
    removeSession(session);
  }

}
