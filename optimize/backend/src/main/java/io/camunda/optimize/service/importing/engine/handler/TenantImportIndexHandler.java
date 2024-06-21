/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.handler;

import io.camunda.optimize.dto.engine.TenantEngineDto;
import io.camunda.optimize.rest.engine.EngineContext;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.importing.AllEntitiesBasedImportIndexHandler;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class TenantImportIndexHandler extends AllEntitiesBasedImportIndexHandler {

  private final EngineContext engineContext;
  private final Map<String, TenantEngineDto> alreadyImportedTenants = new HashMap<>();

  public TenantImportIndexHandler(final EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @Override
  public String getEngineAlias() {
    return engineContext.getEngineAlias();
  }

  public void addImportedTenants(final Collection<TenantEngineDto> tenantDtos) {
    tenantDtos.forEach(tenant -> alreadyImportedTenants.put(tenant.getId(), tenant));
    moveImportIndex(tenantDtos.size());
  }

  public List<TenantEngineDto> filterNewOrChangedTenants(
      final List<TenantEngineDto> engineEntities) {
    final Collection<TenantEngineDto> importedTenantDtos = alreadyImportedTenants.values();
    return engineEntities.stream()
        .filter(tenantDto -> !importedTenantDtos.contains(tenantDto))
        .collect(Collectors.toList());
  }

  @Override
  public void resetImportIndex() {
    super.resetImportIndex();
    alreadyImportedTenants.clear();
  }

  @Override
  protected String getDatabaseImportIndexType() {
    return DatabaseConstants.TENANT_INDEX_NAME;
  }
}
