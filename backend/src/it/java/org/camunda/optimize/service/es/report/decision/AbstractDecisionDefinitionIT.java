/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.decision;

import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.camunda.optimize.test.util.decision.DecisionTypeRef;
import org.camunda.optimize.test.util.decision.DmnModelGenerator;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.test.util.decision.DmnHelper.createSimpleDmnModel;
import static org.camunda.optimize.util.DmnModels.INPUT_AMOUNT_ID;
import static org.camunda.optimize.util.DmnModels.INPUT_CATEGORY_ID;
import static org.camunda.optimize.util.DmnModels.INPUT_GUEST_WITH_CHILDREN_ID;
import static org.camunda.optimize.util.DmnModels.INPUT_INVOICE_DATE_ID;
import static org.camunda.optimize.util.DmnModels.INPUT_NUMBER_OF_GUESTS_ID;
import static org.camunda.optimize.util.DmnModels.INPUT_SEASON_ID;
import static org.camunda.optimize.util.DmnModels.INPUT_VARIABLE_AMOUNT;
import static org.camunda.optimize.util.DmnModels.INPUT_VARIABLE_GUEST_WITH_CHILDREN;
import static org.camunda.optimize.util.DmnModels.INPUT_VARIABLE_INTEGER_INPUT;
import static org.camunda.optimize.util.DmnModels.INPUT_VARIABLE_INVOICE_CATEGORY;
import static org.camunda.optimize.util.DmnModels.INPUT_VARIABLE_INVOICE_DATE;
import static org.camunda.optimize.util.DmnModels.INPUT_VARIABLE_NUMBER_OF_GUESTS;
import static org.camunda.optimize.util.DmnModels.INPUT_VARIABLE_SEASON;
import static org.camunda.optimize.util.DmnModels.INPUT_VARIABLE_STRING_INPUT;
import static org.camunda.optimize.util.DmnModels.INTEGER_INPUT_ID;
import static org.camunda.optimize.util.DmnModels.INTEGER_OUTPUT_ID;
import static org.camunda.optimize.util.DmnModels.OUTPUT_VARIABLE_INTEGER_OUTPUT;
import static org.camunda.optimize.util.DmnModels.OUTPUT_VARIABLE_STRING_OUTPUT;
import static org.camunda.optimize.util.DmnModels.STRING_INPUT_ID;
import static org.camunda.optimize.util.DmnModels.STRING_OUTPUT_ID;
import static org.camunda.optimize.util.DmnModels.createDefaultDmnModel;
import static org.camunda.optimize.util.SuppressionConstants.UNUSED;

public abstract class AbstractDecisionDefinitionIT extends AbstractIT {

  private static String getInputVariableNameForId(String inputId) {
    switch (inputId) {
      case INPUT_AMOUNT_ID:
        return INPUT_VARIABLE_AMOUNT;
      case INPUT_CATEGORY_ID:
        return INPUT_VARIABLE_INVOICE_CATEGORY;
      case INPUT_INVOICE_DATE_ID:
        return INPUT_VARIABLE_INVOICE_DATE;
      case INPUT_SEASON_ID:
        return INPUT_VARIABLE_SEASON;
      case INPUT_NUMBER_OF_GUESTS_ID:
        return INPUT_VARIABLE_NUMBER_OF_GUESTS;
      case INPUT_GUEST_WITH_CHILDREN_ID:
        return INPUT_VARIABLE_GUEST_WITH_CHILDREN;
      case STRING_INPUT_ID:
        return INPUT_VARIABLE_STRING_INPUT;
      case INTEGER_INPUT_ID:
        return INPUT_VARIABLE_INTEGER_INPUT;
      case STRING_OUTPUT_ID:
        return OUTPUT_VARIABLE_STRING_OUTPUT;
      case INTEGER_OUTPUT_ID:
        return OUTPUT_VARIABLE_INTEGER_OUTPUT;
      default:
        throw new IllegalStateException("Unsupported inputVariableId: " + inputId);
    }
  }

  protected String deployAndStartMultiTenantDefinition(final List<String> deployedTenants) {
    final String decisionDefinitionKey = "multiTenantProcess";
    deployedTenants.stream()
      .filter(Objects::nonNull)
      .forEach(tenantId -> engineIntegrationExtension.createTenant(tenantId));
    deployedTenants
      .forEach(tenant -> {
        final DecisionDefinitionEngineDto decisionDefinitionEngineDto = deployDecisionDefinitionWithDifferentKey(
          decisionDefinitionKey, tenant
        );
        startDecisionInstanceWithInputVars(
          decisionDefinitionEngineDto.getId(), createInputs(100.0, "Misc")
        );
      });
    return decisionDefinitionKey;
  }

  protected DecisionDefinitionEngineDto deployAndStartSimpleDecisionDefinition(String decisionKey) {
    return deployAndStartSimpleDecisionDefinition(decisionKey, null);
  }

  protected DecisionDefinitionEngineDto deployAndStartSimpleDecisionDefinition(String decisionKey, String tenantId) {
    final DmnModelInstance modelInstance = createSimpleDmnModel(decisionKey);
    return engineIntegrationExtension.deployAndStartDecisionDefinition(modelInstance, tenantId);
  }

  protected DecisionDefinitionEngineDto deployDecisionDefinitionWithDifferentKey(final String key) {
    return deployDecisionDefinitionWithDifferentKey(key, null);
  }

  protected DecisionDefinitionEngineDto deployDecisionDefinitionWithDifferentKey(final String key, String tenantId) {
    final DmnModelInstance dmnModelInstance = createDefaultDmnModel();
    dmnModelInstance.getDefinitions().getDrgElements().stream()
      .findFirst()
      .ifPresent(drgElement -> drgElement.setId(key));
    return engineIntegrationExtension.deployDecisionDefinition(dmnModelInstance, tenantId);
  }

  protected DecisionDefinitionEngineDto deploySimpleInputDecisionDefinition(final String inputClauseId,
                                                                            final String camInputVariable,
                                                                            final DecisionTypeRef inputType) {
    final DmnModelGenerator dmnModelGenerator = DmnModelGenerator.create()
      .decision()
      .addInput("input", inputClauseId, camInputVariable, inputType)
      .addOutput("output", DecisionTypeRef.STRING)
      .buildDecision();
    return engineIntegrationExtension.deployDecisionDefinition(dmnModelGenerator.build());
  }

  protected DecisionDefinitionEngineDto deploySimpleOutputDecisionDefinition(final String outputClauseId,
                                                                             final String camInputVariable,
                                                                             final String ruleExpression,
                                                                             final DecisionTypeRef type) {
    // @formatter:off
    final DmnModelGenerator dmnModelGenerator = DmnModelGenerator.create()
      .decision()
      .addInput("input", camInputVariable, type)
      .addOutput("output", outputClauseId, camInputVariable, type)
      .rule()
        .addStringInputEntry(type == DecisionTypeRef.STRING ? String.format("\"%s\"", ruleExpression) : ruleExpression)
        .addStringOutputEntry(camInputVariable)
      .buildRule()
      .buildDecision();
    // @formatter:on
    return engineIntegrationExtension.deployDecisionDefinition(dmnModelGenerator.build());
  }

  protected HashMap<String, InputVariableEntry> createInputs(final double amountValue,
                                                             final String category) {
    return new HashMap<String, InputVariableEntry>() {{
      put(INPUT_AMOUNT_ID, new InputVariableEntry(INPUT_AMOUNT_ID, "Invoice Amount", VariableType.DOUBLE, amountValue));
      put(
        INPUT_CATEGORY_ID,
        new InputVariableEntry(INPUT_CATEGORY_ID, "Invoice Category", VariableType.STRING, category)
      );
    }};
  }

  protected HashMap<String, InputVariableEntry> createInputsWithDate(final double amountValue,
                                                                     final String invoiceDateTime) {
    final HashMap<String, InputVariableEntry> inputs = createInputs(amountValue, "Misc");
    inputs.put(
      INPUT_INVOICE_DATE_ID,
      new InputVariableEntry(INPUT_INVOICE_DATE_ID, "Invoice Date", VariableType.DATE, invoiceDateTime)
    );
    return inputs;
  }

  protected void startDecisionInstanceWithInputVars(final String id,
                                                    final Map<String, InputVariableEntry> inputVariables) {
    Map<String, Object> variables  = new HashMap<>();
    for (Map.Entry<String, InputVariableEntry> entry : inputVariables.entrySet()) {
      variables.put(getInputVariableNameForId(entry.getKey()), entry.getValue().getValue());
    }
    engineIntegrationExtension.startDecisionInstance(
      id,
      variables
    );
  }

  protected DecisionReportDataDto createReportWithAllVersionSet(DecisionDefinitionEngineDto decisionDefinitionDto) {
    return DecisionReportDataBuilder
      .create()
      .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
      .setDecisionDefinitionVersion(ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.RAW_DATA)
      .build();
  }

  @SuppressWarnings(UNUSED)
  protected static Stream<AggregateByDateUnit> staticAggregateByDateUnits() {
    return Arrays.stream(AggregateByDateUnit.values()).filter(unit -> !AggregateByDateUnit.AUTOMATIC.equals(unit));
  }

}
