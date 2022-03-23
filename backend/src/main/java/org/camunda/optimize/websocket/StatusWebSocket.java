/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.importing.ImportSchedulerManagerService;
import org.camunda.optimize.service.status.StatusCheckingService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.web.socket.server.standard.SpringConfigurator;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.camunda.optimize.jetty.OptimizeResourceConstants.STATUS_WEBSOCKET_PATH;

@RequiredArgsConstructor
@ServerEndpoint(value = STATUS_WEBSOCKET_PATH, configurator = SpringConfigurator.class)
@Slf4j
public class StatusWebSocket {

  private final StatusCheckingService statusCheckingService;
  private final ObjectMapper objectMapper;
  private final ConfigurationService configurationService;
  private final ImportSchedulerManagerService importSchedulerManagerService;

  private final Map<String, StatusNotifier> statusReportJobs = new ConcurrentHashMap<>();

  @OnOpen
  public void onOpen(Session session) {
    if (statusReportJobs.size() < configurationService.getMaxStatusConnections()) {
      StatusNotifier job = new StatusNotifier(
        statusCheckingService,
        objectMapper,
        session
      );
      statusReportJobs.put(session.getId(), job);
      importSchedulerManagerService.subscribeImportObserver(job);
      log.debug("starting to report status for session [{}]", session.getId());
    } else {
      log.debug("cannot create status report job for [{}], max connections exceeded", session.getId());
      try {
        session.close();
      } catch (IOException e) {
        log.error("can't close status report web socket session");
      }
    }

  }

  @OnClose
  public void onClose(Session session) {
    log.debug("stopping status reporting for session [{}]", session.getId());
    removeSession(session);
  }

  private void removeSession(Session session) {
    if (statusReportJobs.containsKey(session.getId())) {
      StatusNotifier job = statusReportJobs.remove(session.getId());
      importSchedulerManagerService.unsubscribeImportObserver(job);
    }
  }

  @OnError
  public void onError(Throwable t, Session session) {
    String message = "Web socket connection terminated prematurely!";
    if (log.isWarnEnabled()) {
      log.warn(message);
    } else if (log.isDebugEnabled()) {
      log.debug(message, t);
    }
    removeSession(session);
  }

}
