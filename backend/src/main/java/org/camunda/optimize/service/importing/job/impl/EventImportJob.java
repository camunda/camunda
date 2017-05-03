package org.camunda.optimize.service.importing.job.impl;

import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.service.es.writer.EventsWriter;
import org.camunda.optimize.service.importing.job.ImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventImportJob extends ImportJob<EventDto>{

  private EventsWriter eventsWriter;
  private Logger logger = LoggerFactory.getLogger(EventImportJob.class);

  public EventImportJob(EventsWriter eventsWriter) {
    this.eventsWriter = eventsWriter;
  }

  @Override
  protected void executeImport() {
    try {
      eventsWriter.importEvents(newOptimizeEntities);
    } catch (Exception e) {
      logger.error("error while writing events to elasticsearch", e);
    }
  }
}
