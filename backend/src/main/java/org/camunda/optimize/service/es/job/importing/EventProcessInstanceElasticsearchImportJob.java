/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.query.event.process.EventProcessInstanceDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.EventProcessInstanceWriter;

import java.util.List;

public class EventProcessInstanceElasticsearchImportJob extends ElasticsearchImportJob<EventProcessInstanceDto> {

  private final EventProcessInstanceWriter eventProcessInstanceWriter;

  public EventProcessInstanceElasticsearchImportJob(final EventProcessInstanceWriter eventProcessInstanceWriter,
                                                    final Runnable callback) {
    super(callback);
    this.eventProcessInstanceWriter = eventProcessInstanceWriter;
  }

  @Override
  protected void persistEntities(List<EventProcessInstanceDto> newOptimizeEntities) {
    eventProcessInstanceWriter.importProcessInstances(newOptimizeEntities);
  }
}
