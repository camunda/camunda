/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.IdentityLinkLogEntryDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.usertask.IdentityLinkLogWriter;

import java.util.List;

public class IdentityLinkLogElasticsearchImportJob extends ElasticsearchImportJob<IdentityLinkLogEntryDto> {
  private IdentityLinkLogWriter identityLinkLogWriter;

  public IdentityLinkLogElasticsearchImportJob(final IdentityLinkLogWriter identityLinkLogWriter,
                                               Runnable callback) {
    super(callback);
    this.identityLinkLogWriter = identityLinkLogWriter;
  }

  @Override
  protected void persistEntities(List<IdentityLinkLogEntryDto> newOptimizeEntities) {
    identityLinkLogWriter.importIdentityLinkLogs(newOptimizeEntities);
  }
}
