/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.ingested;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.SneakyThrows;
import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.importing.ingested.service.ExternalVariableUpdateImportService;
import org.camunda.optimize.service.util.InstanceIndexUtil;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.DOUBLE;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.LONG;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.OBJECT;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.STRING;

public class ExternalVariableDataImportIT extends AbstractIngestedDataImportIT {

  @Test
  public void singleIngestedVariableIsWrittenToInstanceIndex() {
    // given
    final ExternalProcessVariableRequestDto externalVariable = ingestionClient.createPrimitiveExternalVariable();
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
      .mapToObj(i -> ingestionClient.createPrimitiveExternalVariable().setId("id" + i))
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
      .mapToObj(i -> ingestionClient.createPrimitiveExternalVariable()
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

    final ExternalProcessVariableRequestDto externalVariable1 = ingestionClient.createPrimitiveExternalVariable();
    final String externalVariableName2 = "secondVar";
    final ExternalProcessVariableRequestDto externalVariable2 = ingestionClient.createPrimitiveExternalVariable()
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
      deployAndStartSimpleServiceProcessTaskWithVariables(Map.of(engineVariableName, "aString"));
    final ExternalProcessVariableRequestDto externalVariable1 = ingestionClient.createPrimitiveExternalVariable()
      .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
      .setProcessInstanceId(processInstance.getId());
    final ExternalProcessVariableRequestDto externalVariable2 = ingestionClient.createPrimitiveExternalVariable()
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

  @Test
  public void ingestedVariableIsUpdated_multipleUpdatesInConsecutiveImports() {
    // given two updates for the same variable which are ingested in separate batches and imported in two consecutive
    // import rounds
    ExternalProcessVariableRequestDto variable = ingestionClient.createPrimitiveExternalVariable()
      .setId("1")
      .setValue("firstValue");

    ingestionClient.ingestVariables(Collections.singletonList(variable));
    importIngestedDataFromScratchRefreshIndicesBeforeAndAfter();

    variable.setValue("secondValue");
    ingestionClient.ingestVariables(Collections.singletonList(variable));

    // when
    importIngestedDataFromLastIndexRefreshIndicesBeforeAndAfter();

    // then
    assertThat(elasticSearchIntegrationTestExtension.indexExists(
      InstanceIndexUtil.getProcessInstanceIndexAliasName(variable.getProcessDefinitionKey())
    )).isTrue();
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(instance -> {
        assertThat(instance.getProcessInstanceId()).isEqualTo(variable.getProcessInstanceId());
        assertThat(instance.getVariables())
          .singleElement()
          .extracting(SimpleProcessVariableDto.Fields.value)
          .isEqualTo(Collections.singletonList("secondValue"));
      });
  }

  @Test
  public void ingestedVariableIsUpdated_multipleUpdatesInSameImport() {
    // given two updates for the same variable which are ingested in separate batches but imported in the same import
    // round
    ExternalProcessVariableRequestDto variable = ingestionClient.createPrimitiveExternalVariable()
      .setId("1")
      .setValue("firstValue");

    ingestionClient.ingestVariables(Collections.singletonList(variable));
    variable.setValue("secondValue");
    ingestionClient.ingestVariables(Collections.singletonList(variable));

    // when
    importIngestedDataFromScratchRefreshIndicesBeforeAndAfter();

    // then
    assertThat(elasticSearchIntegrationTestExtension.indexExists(
      InstanceIndexUtil.getProcessInstanceIndexAliasName(variable.getProcessDefinitionKey())
    )).isTrue();
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(instance -> {
        assertThat(instance.getProcessInstanceId()).isEqualTo(variable.getProcessInstanceId());
        assertThat(instance.getVariables())
          .singleElement()
          .extracting(SimpleProcessVariableDto.Fields.value)
          .isEqualTo(Collections.singletonList("secondValue"));
      });
  }

  @SneakyThrows
  @Test
  public void objectVariablesAreFlattenedAndImported() {
    // given
    final Map<String, Object> person = new HashMap<>();
    person.put("name", "Pond");
    person.put("age", 28);
    person.put("likes", List.of("optimize", "garlic"));
    final ExternalProcessVariableRequestDto externalVariable = ingestionClient.createObjectExternalVariable(
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(person));
    ingestionClient.ingestVariables(List.of(externalVariable));

    // when
    importIngestedDataFromScratchRefreshIndicesBeforeAndAfter();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances()
                 .stream()
                 .findFirst()
                 .map(ProcessInstanceDto::getVariables)
                 .orElse(Collections.emptyList()))
      .extracting(
        SimpleProcessVariableDto::getName,
        SimpleProcessVariableDto::getType,
        SimpleProcessVariableDto::getValue
      )
      .containsExactlyInAnyOrder(
        Tuple.tuple("objectVarName", OBJECT.getId(), Collections.singletonList(externalVariable.getValue())),
        Tuple.tuple("objectVarName.name", STRING.getId(), Collections.singletonList("Pond")),
        Tuple.tuple("objectVarName.age", DOUBLE.getId(), Collections.singletonList("28.0")),
        Tuple.tuple("objectVarName.likes", STRING.getId(), List.of("optimize", "garlic")),
        Tuple.tuple("objectVarName.likes._listSize", LONG.getId(), Collections.singletonList("2"))
      );
  }

  @SneakyThrows
  @Test
  public void objectVariablesAreUpdated() {
    // given
    final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    final Map<String, Object> person = new HashMap<>();
    person.put("name", "Pond");
    person.put("age", 28);
    person.put("likes", List.of("optimize", "garlic"));
    ExternalProcessVariableRequestDto externalVariable =
      ingestionClient.createObjectExternalVariable(objectMapper.writeValueAsString(person));
    ingestionClient.ingestVariables(List.of(externalVariable));
    importIngestedDataFromScratchRefreshIndicesBeforeAndAfter();

    // variable properties are updated
    person.put("age", 29);
    person.put("likes", List.of("optimize", "garlic", "tofu"));
    externalVariable = ingestionClient.createObjectExternalVariable(objectMapper.writeValueAsString(person));
    ingestionClient.ingestVariables(List.of(externalVariable));

    // when
    importIngestedDataFromLastIndexRefreshIndicesBeforeAndAfter();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances()
                 .stream()
                 .findFirst()
                 .map(ProcessInstanceDto::getVariables)
                 .orElse(Collections.emptyList()))
      .extracting(
        SimpleProcessVariableDto::getName,
        SimpleProcessVariableDto::getType,
        SimpleProcessVariableDto::getValue,
        SimpleProcessVariableDto::getVersion
      )
      .containsExactlyInAnyOrder(
        Tuple.tuple("objectVarName", OBJECT.getId(), Collections.singletonList(externalVariable.getValue()), 1000L),
        Tuple.tuple("objectVarName.name", STRING.getId(), Collections.singletonList("Pond"), 1000L),
        Tuple.tuple("objectVarName.age", DOUBLE.getId(), Collections.singletonList("29.0"), 1000L),
        Tuple.tuple("objectVarName.likes", STRING.getId(), List.of("optimize", "garlic", "tofu"), 1000L),
        Tuple.tuple("objectVarName.likes._listSize", LONG.getId(), Collections.singletonList("3"), 1000L)
      );
  }
}
