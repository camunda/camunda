/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.job;

import java.util.List;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.writer.TenantWriter;
import org.camunda.optimize.service.importing.DatabaseImportJob;

public class TenantDatabaseImportJob extends DatabaseImportJob<TenantDto> {

  private final TenantWriter tenantWriter;

  public TenantDatabaseImportJob(
      final TenantWriter tenantWriter,
      final Runnable importCompleteCallback,
      final DatabaseClient databaseClient) {
    super(importCompleteCallback, databaseClient);
    this.tenantWriter = tenantWriter;
  }

  @Override
  protected void persistEntities(final List<TenantDto> newOptimizeEntities) {
    tenantWriter.writeTenants(newOptimizeEntities);
  }
}
