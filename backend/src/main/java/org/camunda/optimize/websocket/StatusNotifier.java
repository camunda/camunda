package org.camunda.optimize.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.status.StatusWithProgressDto;
import org.camunda.optimize.service.engine.importing.EngineImportSchedulerFactory;
import org.camunda.optimize.service.engine.importing.service.ImportObserver;
import org.camunda.optimize.service.status.StatusCheckingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Session;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StatusNotifier implements ImportObserver {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  private StatusCheckingService statusCheckingService;
  private ObjectMapper objectMapper;
  private Session session;

  private Map<String, Boolean> importStatusMap;

  public StatusNotifier(
    StatusCheckingService statusCheckingService,
    ObjectMapper objectMapper,
    Session session
  ) {
    this.statusCheckingService = statusCheckingService;
    this.objectMapper = objectMapper;
    this.session = session;
    importStatusMap = statusCheckingService.getConnectionStatusWithProgress().getIsImporting();
    sendStatus();
  }

  @Override
  public synchronized void importInProgress(String engineAlias) {
    importStatusMap.put(engineAlias, true);
    sendStatus();
  }

  @Override
  public synchronized void importIsIdle(String engineAlias) {
    importStatusMap.put(engineAlias, false);
    sendStatus();
  }

  private void sendStatus() {
    StatusWithProgressDto result = new StatusWithProgressDto();
    result.setConnectionStatus(statusCheckingService.getConnectionStatus());
    result.setIsImporting(importStatusMap);

    try {
      session.getBasicRemote().sendText(objectMapper.writeValueAsString(result));
    } catch (IOException e) {
      logger.error("can't write status to web socket", e);
    }
  }
}
