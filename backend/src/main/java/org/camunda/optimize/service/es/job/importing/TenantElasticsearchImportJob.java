/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.TenantWriter;

import java.util.List;

public class TenantElasticsearchImportJob extends ElasticsearchImportJob<TenantDto> {

  private final TenantWriter tenantWriter;

  public TenantElasticsearchImportJob(final TenantWriter tenantWriter, final Runnable importCompleteCallback) {
    super(importCompleteCallback);
    this.tenantWriter = tenantWriter;
  }

  @Override
  protected void persistEntities(final List<TenantDto> newOptimizeEntities) {
    tenantWriter.writeTenants(newOptimizeEntities);
  }
}
