/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class ProcessEngineImportRestServiceIT extends AbstractIT {

  private static final String PROCESS_ID = "aProcessId";

  @Test
  public void importDataFromEngine() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(PROCESS_ID));

    // when
    importAllEngineEntitiesFromScratch();

    // when
    List<ProcessDefinitionOptimizeDto> definitions = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetProcessDefinitionsRequest()
      .executeAndReturnList(ProcessDefinitionOptimizeDto.class, Response.Status.OK.getStatusCode());
    // then
    assertThat(definitions).isNotNull().hasSize(1);
    assertThat(definitions.get(0).getId()).isNotNull();
    assertThat(definitions.get(0).getKey()).isEqualTo(PROCESS_ID);
    assertThat(definitions.get(0).getVersion()).isNotEqualTo("0");
  }

}
