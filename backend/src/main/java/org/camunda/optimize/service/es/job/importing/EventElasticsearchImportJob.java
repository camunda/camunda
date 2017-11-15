package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.service.es.writer.EventsWriter;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventElasticsearchImportJob extends ElasticsearchImportJob<FlowNodeEventDto> {

  private EventsWriter eventsWriter;
  private Logger logger = LoggerFactory.getLogger(EventElasticsearchImportJob.class);

  public EventElasticsearchImportJob(EventsWriter eventsWriter) {
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
