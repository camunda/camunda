/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.ingested;

import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.importing.ingested.service.ExternalVariableUpdateImportService;
import org.camunda.optimize.service.util.InstanceIndexUtil;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

public class ExternalVariableDataImportIT extends AbstractIngestedDataImportIT {

  @Test
  public void singleIngestedVariableIsWrittenToInstanceIndex() {
    // given
    final ExternalProcessVariableRequestDto externalVariable = ingestionClient.createExternalVariable();
    ingestionClient.ingestVariables(List.of(externalVariable));

    // when
    importIngestedDataFromScratchRefreshIndicesBeforeAndAfter();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(instance -> {
        assertThat(instance.getProcessInstanceId()).isEqualTo(externalVariable.getProcessInstanceId());
        // we expect the key to be null as this is only set via the instance import, this will also prevent these
        // incomplete instances from showing up in reports if there is no real matching instance for them
        assertThat(instance.getProcessDefinitionKey()).isNull();
        assertThat(instance.getVariables())
          .singleElement()
          .satisfies(variableInstance -> {
            assertThat(variableInstance.getId()).isEqualTo(externalVariable.getId());
            assertThat(variableInstance.getName()).isEqualTo(externalVariable.getName());
            assertThat(variableInstance.getType()).isEqualTo(externalVariable.getType().getId());
            assertThat(variableInstance.getVersion()).isEqualTo(ExternalVariableUpdateImportService.DEFAULT_VERSION);
          });
      });
  }

  @Test
  public void multipleIngestedVariablesForOneInstanceIsWrittenToInstanceIndex() {
    // given
    final List<ExternalProcessVariableRequestDto> variables = IntStream.range(0, 10)
      .mapToObj(i -> ingestionClient.createExternalVariable().setId("id" + i))
      .collect(toList());

    ingestionClient.ingestVariables(variables);

    // when
    importIngestedDataFromScratchRefreshIndicesBeforeAndAfter();

    // then
    assertThat(elasticSearchIntegrationTestExtension.indexExists(
      InstanceIndexUtil.getProcessInstanceIndexAliasName(variables.get(0).getProcessDefinitionKey())
    )).isTrue();
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(instance -> {
        assertThat(instance.getProcessInstanceId()).isEqualTo(variables.get(0).getProcessInstanceId());
        assertThat(instance.getVariables()).hasSize(variables.size());
      });
  }

  @Test
  public void multipleIngestedVariablesForDifferentDefinitionInstancesAreWrittenToInstanceIndices() {
    // given
    final List<ExternalProcessVariableRequestDto> variables = IntStream.range(0, 10)
      .mapToObj(i -> ingestionClient.createExternalVariable()
        .setName(String.valueOf(i))
        .setProcessDefinitionKey("key" + i)
        .setProcessInstanceId(String.valueOf(i))
      )
      .collect(toList());
    ingestionClient.ingestVariables(variables);

    // when
    importIngestedDataFromScratchRefreshIndicesBeforeAndAfter();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .allSatisfy(processInstanceDto -> {
        // as process instance id and variable name are identical, check if variables are written to expected instances
        assertThat(processInstanceDto.getVariables())
          .extracting(SimpleProcessVariableDto::getName)
          .containsExactly(processInstanceDto.getProcessInstanceId());
      })
      // ensure that all expected instances were there
      .extracting(ProcessInstanceDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(
        variables.stream().map(ExternalProcessVariableRequestDto::getProcessInstanceId).toArray(String[]::new)
      );
  }

  @Test
  public void ingestedVariableDataIsWrittenToInstanceIndexWithConfiguredMaxPageSize() {
    // given
    embeddedOptimizeExtension.getConfigurationService()
      .getExternalVariableConfiguration()
      .getImportConfiguration()
      .setMaxPageSize(1);

    final ExternalProcessVariableRequestDto externalVariable1 = ingestionClient.createExternalVariable();
    final String externalVariableName2 = "secondVar";
    final ExternalProcessVariableRequestDto externalVariable2 = ingestionClient.createExternalVariable()
      .setId(externalVariableName2).setName(externalVariableName2);
    ingestionClient.ingestVariables(List.of(externalVariable1, externalVariable2));

    // when
    importIngestedDataFromScratchRefreshIndicesBeforeAndAfter();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(instance -> assertThat(instance.getVariables())
        .extracting(SimpleProcessVariableDto::getId)
        .containsExactly(externalVariable1.getId())
      );

    // when
    importIngestedDataFromScratchRefreshIndicesBeforeAndAfter();

    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(instance -> assertThat(instance.getVariables())
        .extracting(SimpleProcessVariableDto::getId)
        .containsExactlyInAnyOrder(externalVariable1.getId(), externalVariable2.getId())
      );
  }

  @Test
  public void ingestedVariableDataIsMergedWithEngineDataToInstanceIndex() {
    // given
    final String engineVariableName = "aVariable";
    final ProcessInstanceEngineDto processInstance =
      deployAndStartSimpleServiceTaskWithVariables(Map.of(engineVariableName, "aString"));
    final ExternalProcessVariableRequestDto externalVariable1 = ingestionClient.createExternalVariable()
      .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
      .setProcessInstanceId(processInstance.getId());
    final ExternalProcessVariableRequestDto externalVariable2 = ingestionClient.createExternalVariable()
      .setId("secondVariable")
      .setName("secondVariable")
      .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
      .setProcessInstanceId(processInstance.getId());
    ingestionClient.ingestVariables(List.of(externalVariable1, externalVariable2));

    // when
    embeddedOptimizeExtension.importAllEngineData();
    importIngestedDataFromScratchRefreshIndicesBeforeAndAfter();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(instance -> {
        assertThat(instance.getProcessInstanceId()).isEqualTo(processInstance.getId());
        assertThat(instance.getProcessDefinitionKey()).isEqualTo(processInstance.getProcessDefinitionKey());
        assertThat(instance.getProcessDefinitionVersion()).isEqualTo(processInstance.getProcessDefinitionVersion());
        assertThat(instance.getFlowNodeInstances()).isNotEmpty();
        assertThat(instance.getVariables())
          .extracting(SimpleProcessVariableDto::getName)
          .containsExactlyInAnyOrder(engineVariableName, externalVariable1.getName(), externalVariable2.getName());
      });
  }

}
