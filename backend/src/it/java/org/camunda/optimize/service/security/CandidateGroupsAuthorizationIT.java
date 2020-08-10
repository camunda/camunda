/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.dto.optimize.query.definition.AssigneeRequestDto;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TENANT_INDEX_NAME;

public class CandidateGroupsAuthorizationIT extends AbstractIT {

  @Test
  public void getAssigneesForUnauthorizedTenant() {
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      TENANT_INDEX_NAME,
      "newTenant",
      new TenantDto("newTenant", "newTenant", DEFAULT_ENGINE_ALIAS)
    );

    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);

    AssigneeRequestDto requestDto = new AssigneeRequestDto(
      "aProcess",
      Collections.singletonList("ALL"),
      Collections.singletonList("newTenant")
    );

    importAllEngineEntitiesFromScratch();

    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetCandidateGroupsRequest(requestDto)
      .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void getCandidateGroupsWithoutAuthentication() {
    startSimpleUserTaskProcessWithCandidateGroup();

    importAllEngineEntitiesFromScratch();

    AssigneeRequestDto requestDto = new AssigneeRequestDto(
      "aProcess",
      Collections.singletonList("ALL"),
      Collections.singletonList(null)
    );

    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildGetCandidateGroupsRequest(requestDto)
      .execute();

    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  private void startSimpleUserTaskProcessWithCandidateGroup() {
    engineIntegrationExtension.deployAndStartProcess(
      Bpmn.createExecutableProcess("aProcess")
        .startEvent()
        .userTask().camundaCandidateGroups("marketing")
        .userTask().camundaCandidateGroups("sales")
        .endEvent()
        .done()
    );
  }
}
