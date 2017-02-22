package org.camunda.optimize.service.importing.job.impl;

import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.service.es.writer.EventsWriter;
import org.camunda.optimize.service.importing.job.ImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class EventImportJob implements ImportJob<EventDto>{

  private EventsWriter eventsWriter;
  private Logger logger = LoggerFactory.getLogger(EventImportJob.class);

  private List<EventDto> optimizeEntities;

  public EventImportJob(EventsWriter eventsWriter) {
    this.eventsWriter = eventsWriter;
  }

  @Override
  public void addEntitiesToImport(List<EventDto> pageOfOptimizeEntities) {
    this.optimizeEntities = pageOfOptimizeEntities;
  }

  @Override
  public void fetchMissingEntityInformation() {
    // do nothing yet
  }

  @Override
  public void executeImport() {
    try {
      eventsWriter.importEvents(optimizeEntities);
    } catch (Exception e) {
      logger.error("error while writing events to elasticsearch", e);
    }
  }
}
