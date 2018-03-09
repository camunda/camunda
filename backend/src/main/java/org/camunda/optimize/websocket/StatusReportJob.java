package org.camunda.optimize.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.status.StatusWithProgressDto;
import org.camunda.optimize.service.status.ImportProgressReporter;
import org.camunda.optimize.service.status.StatusCheckingService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Session;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * @author Askar Akhmerov
 */
public class StatusReportJob extends Thread {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  private StatusCheckingService statusCheckingService;
  private ImportProgressReporter importProgressReporter;
  private ObjectMapper objectMapper;
  private Session session;
  private ConfigurationService configurationService;

  public StatusReportJob(
    StatusCheckingService statusCheckingService,
    ImportProgressReporter importProgressReporter,
    ObjectMapper objectMapper,
    ConfigurationService configurationService,
    Session session
  ) {
    this.statusCheckingService = statusCheckingService;
    this.importProgressReporter = importProgressReporter;
    this.objectMapper = objectMapper;
    this.configurationService = configurationService;
    this.session = session;
  }

  @Override
  public void run() {
    while (session.isOpen()) {
      StatusWithProgressDto result = new StatusWithProgressDto();
      result.setConnectionStatus(statusCheckingService.getConnectionStatus());
      long progress = importProgressReporter.computeImportProgress();
      result.setProgress(progress);

      try {
        session.getBasicRemote().sendText(objectMapper.writeValueAsString(result));
      } catch (IOException e) {
        logger.error("can't write status to web socket", e);
      }

      ChronoUnit chronoUnit = ChronoUnit.valueOf(configurationService.getStatusIntervalUnit().toUpperCase());
      long until = OffsetDateTime.now().until(
        OffsetDateTime.now().plus(configurationService.getStatusIntervalValue(), chronoUnit),
        ChronoUnit.MILLIS
      );

      try {
        this.sleep(until);
      } catch (InterruptedException e) {
        logger.error("interrupting sleep of status reporter", e);
      }
    }
  }
}
