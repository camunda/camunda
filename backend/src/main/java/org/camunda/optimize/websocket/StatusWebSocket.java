package org.camunda.optimize.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.service.status.ImportProgressReporter;
import org.camunda.optimize.service.status.StatusCheckingService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.server.standard.SpringConfigurator;

import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Askar Akhmerov
 */
@ServerEndpoint(value = "/ws/status", configurator = SpringConfigurator.class)
public class StatusWebSocket {

  @Autowired
  private StatusCheckingService statusCheckingService;

  @Autowired
  private ImportProgressReporter importProgressReporter;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ConfigurationService configurationService;


  private Map<String, StatusReportJob> statusReportJobs = new HashMap<>();
  private final Logger logger = LoggerFactory.getLogger(getClass());


  @OnOpen
  public void onOpen(Session session) {
    if (statusReportJobs.size() <= configurationService.getMaxStatusConnections()) {
      StatusReportJob job = new StatusReportJob(
        statusCheckingService,
        importProgressReporter,
        objectMapper,
        configurationService,
        session
      );
      statusReportJobs.put(session.getId(), job);
      job.start();
      logger.debug("starting to report status for session [{}]",session.getId());
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
    if (statusReportJobs.containsKey(session.getId())) {
      statusReportJobs.remove(session.getId());
    }
  }

}
