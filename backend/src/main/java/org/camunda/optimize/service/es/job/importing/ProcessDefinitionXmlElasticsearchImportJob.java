/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.ProcessDefinitionXmlWriter;

import java.util.List;

public class ProcessDefinitionXmlElasticsearchImportJob extends ElasticsearchImportJob<ProcessDefinitionOptimizeDto> {

  private ProcessDefinitionXmlWriter processDefinitionXmlWriter;

  public ProcessDefinitionXmlElasticsearchImportJob(final ProcessDefinitionXmlWriter processDefinitionXmlWriter,
                                                    final Runnable importCompleteCallback) {
    super(importCompleteCallback);
    this.processDefinitionXmlWriter = processDefinitionXmlWriter;
  }

  @Override
  protected void persistEntities(List<ProcessDefinitionOptimizeDto> newOptimizeEntities) {
    processDefinitionXmlWriter.importProcessDefinitionXmls(newOptimizeEntities);
  }
}
