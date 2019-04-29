/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.persistence.TenantDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.TenantWriter;

import java.util.List;

public class TenantElasticsearchImportJob extends ElasticsearchImportJob<TenantDto> {

  private final TenantWriter tenantWriter;

  public TenantElasticsearchImportJob(final TenantWriter tenantWriter) {
    super();
    this.tenantWriter = tenantWriter;
  }

  @Override
  protected void persistEntities(final List<TenantDto> newOptimizeEntities) {
    tenantWriter.writeTenants(newOptimizeEntities);
  }
}
