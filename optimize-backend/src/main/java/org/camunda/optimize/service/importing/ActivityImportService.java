package org.camunda.optimize.service.importing;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceDto;
import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.mapper.EventMappingHelper;
import org.camunda.optimize.service.es.EventsWriter;
import org.camunda.optimize.service.util.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Askar Akhmerov
 */
@Component
public class ActivityImportService {
  private final Logger logger = LoggerFactory.getLogger(ActivityImportService.class);

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private EventsWriter eventsWriter;

  @Autowired
  private Client client;

  public void executeImport () {

    List<HistoricActivityInstanceDto> entries = client
        .target(configurationService.getEngineRestApiEndpoint() + configurationService.getEngineName())
        .path(configurationService.getHistoricActivityInstanceEndpoint())
        .request(MediaType.APPLICATION_JSON)
        .get(new GenericType<List<HistoricActivityInstanceDto>>() {});

    List <EventDto> events = new ArrayList<>();
    for (HistoricActivityInstanceDto dto: entries) {
      EventMappingHelper.map(dto,events);
    }

    try {
      eventsWriter.importEvents(events);
    } catch (Exception e) {
      logger.error("error while writing events to elasticsearch", e);
    }
  }

  public Client getClient() {
    return client;
  }

  public void setClient(Client client) {
    this.client = client;
  }
}
