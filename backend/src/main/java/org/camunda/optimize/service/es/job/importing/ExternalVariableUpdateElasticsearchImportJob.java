/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.es.writer.variable.ProcessVariableUpdateWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.List;

public class ExternalVariableUpdateElasticsearchImportJob extends ElasticsearchImportJob<ProcessVariableDto> {

  private final ProcessVariableUpdateWriter variableWriter;
  private final ConfigurationService configurationService;

  public ExternalVariableUpdateElasticsearchImportJob(final ProcessVariableUpdateWriter variableWriter,
                                                      final ConfigurationService configurationService,
                                                      final Runnable callback) {
    super(callback);
    this.variableWriter = variableWriter;
    this.configurationService = configurationService;
  }

  @Override
  protected void persistEntities(List<ProcessVariableDto> variableUpdates) {
    ElasticsearchWriterUtil.executeImportRequestsAsBulk(
      "External variable updates",
      variableWriter.generateVariableUpdateImports(variableUpdates),
      configurationService.getSkipDataAfterNestedDocLimitReached()
    );
  }

}
