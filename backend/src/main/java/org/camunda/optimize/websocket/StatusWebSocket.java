/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.ApplicationContextProvider;
import org.camunda.optimize.service.importing.ImportSchedulerManagerService;
import org.camunda.optimize.service.status.StatusCheckingService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@NoArgsConstructor
@Slf4j
@WebSocket
public class StatusWebSocket {
  private static final String ERROR_MESSAGE = "Web socket connection terminated prematurely!";
  private final StatusCheckingService statusCheckingService = ApplicationContextProvider.getBean(StatusCheckingService.class);
  private final ObjectMapper objectMapper = ApplicationContextProvider.getBean(ObjectMapper.class);
  private final ConfigurationService configurationService = ApplicationContextProvider.getBean(ConfigurationService.class);
  private final ImportSchedulerManagerService importSchedulerManagerService = ApplicationContextProvider.getBean(ImportSchedulerManagerService.class);

  private final Map<String, StatusNotifier> statusReportJobs = new ConcurrentHashMap<>();

  @OnWebSocketConnect
  public void onOpen(final Session session) {
    if (statusReportJobs.size() < configurationService.getMaxStatusConnections()) {
      StatusNotifier job = new StatusNotifier(
        statusCheckingService,
        objectMapper,
        session
      );
      statusReportJobs.put(session.toString(), job);
      importSchedulerManagerService.subscribeImportObserver(job);
      log.debug("starting to report status for session [{}]", session);
    } else {
      log.debug("cannot create status report job for [{}], max connections exceeded", session);
      session.close();
    }

  }

  @OnWebSocketClose
  public void onClose(final Session session, final int statusCode, final String reason) {
    log.debug("stopping status reporting for session");
    removeSession(session);
  }

  private void removeSession(final Session session) {
    if (statusReportJobs.containsKey(session.toString())) {
      StatusNotifier job = statusReportJobs.remove(session.toString());
      importSchedulerManagerService.unsubscribeImportObserver(job);
    }
  }

  @OnWebSocketError
  public void onError(final Session session, final Throwable t) {
    if (log.isWarnEnabled()) {
      log.warn(ERROR_MESSAGE);
    } else if (log.isDebugEnabled()) {
      log.debug(ERROR_MESSAGE, t);
    }
    removeSession(session);
  }

}
