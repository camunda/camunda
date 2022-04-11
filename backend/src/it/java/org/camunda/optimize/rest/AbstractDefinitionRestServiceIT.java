/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.optimize.TenantDto;

import java.util.Collections;

import static org.camunda.optimize.service.util.importing.EngineConstants.ALL_PERMISSION;
import static org.camunda.optimize.service.util.importing.EngineConstants.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_INDEX_NAME;

public abstract class AbstractDefinitionRestServiceIT extends AbstractIT {

  protected static final String VERSION_TAG = "aVersionTag";
  protected static final String EXPECTED_DEFINITION_NOT_FOUND_MESSAGE = "Could not find xml for";

  protected abstract int getDefinitionResourceType();

  protected void grantSingleDefinitionAuthorizationsForUser(final String userId, final String definitionKey) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(getDefinitionResourceType());
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(definitionKey);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId(userId);
    engineIntegrationExtension.createAuthorization(authorizationDto);
  }

  protected void createTenant(final TenantDto tenantDto) {
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(TENANT_INDEX_NAME, tenantDto.getId(), tenantDto);
  }
}
