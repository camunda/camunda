/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.decision;

import com.fasterxml.jackson.core.type.TypeReference;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportNumberResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedDecisionReportEvaluationResultDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.decision.DecisionTypeRef;
import org.camunda.optimize.test.util.decision.DmnModelGenerator;
import org.junit.Rule;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import static java.util.stream.Collectors.toMap;
import static org.camunda.optimize.test.util.decision.DmnHelper.createSimpleDmnModel;

public abstract class AbstractDecisionDefinitionIT {
  protected static final String OUTPUT_CLASSIFICATION_ID = "clause3";
  protected static final String OUTPUT_AUDIT_ID = "OutputClause_1ur6jbl";
  protected static final String INPUT_AMOUNT_ID = "clause1";
  protected static final String INPUT_CATEGORY_ID = "InputClause_15qmk0v";
  protected static final String INPUT_INVOICE_DATE_ID = "InputClause_0qixz9e";
  protected static final String INPUT_VARIABLE_INVOICE_CATEGORY = "invoiceCategory";
  protected static final String INPUT_VARIABLE_AMOUNT = "amount";
  protected static final String INPUT_VARIABLE_INVOICE_DATE = "invoiceDate";

  // dish variables
  protected static final String INPUT_SEASON_ID = "InputData_0rin549";
  protected static final String INPUT_NUMBER_OF_GUESTS_ID = "InputData_1axnom3";
  protected static final String INPUT_GUEST_WITH_CHILDREN_ID = "InputData_0pgvdj9";
  protected static final String INPUT_VARIABLE_SEASON = "season";
  protected static final String INPUT_VARIABLE_NUMBER_OF_GUESTS = "guestCount";
  protected static final String INPUT_VARIABLE_GUEST_WITH_CHILDREN = "guestsWithChildren";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule(engineRule.getEngineName());

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule)
    .around(engineRule)
    .around(embeddedOptimizeRule)
    .around(engineDatabaseRule);

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
      default:
        throw new IllegalStateException("Unsupported inputVariableId: " + inputId);
    }
  }

  protected String deployAndStartMultiTenantDefinition(final List<String> deployedTenants) {
    final String decisionDefinitionKey = "multiTenantProcess";
    deployedTenants.stream()
      .filter(Objects::nonNull)
      .forEach(tenantId -> engineRule.createTenant(tenantId));
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
    return engineRule.deployAndStartDecisionDefinition(modelInstance, tenantId);
  }

  protected DecisionDefinitionEngineDto deployDecisionDefinitionWithDifferentKey(final String key) {
    return deployDecisionDefinitionWithDifferentKey(key, null);
  }

  protected DecisionDefinitionEngineDto deployDecisionDefinitionWithDifferentKey(final String key, String tenantId) {
    final DmnModelInstance dmnModelInstance = Dmn.readModelFromStream(
      getClass().getClassLoader().getResourceAsStream(EngineIntegrationRule.DEFAULT_DMN_DEFINITION_PATH)
    );
    dmnModelInstance.getDefinitions().getDrgElements().stream()
      .findFirst()
      .ifPresent(drgElement -> drgElement.setId(key));
    return engineRule.deployDecisionDefinition(dmnModelInstance, tenantId);
  }

  protected DecisionDefinitionEngineDto deploySimpleInputDecisionDefinition(final String inputClauseId,
                                                                            final String camInputVariable,
                                                                            final DecisionTypeRef inputType) {
    final DmnModelGenerator dmnModelGenerator = DmnModelGenerator.create()
      .decision()
      .addInput("input", inputClauseId, camInputVariable, inputType)
      .addOutput("output", DecisionTypeRef.STRING)
      .buildDecision();
    return engineRule.deployDecisionDefinition(dmnModelGenerator.build());
  }

  protected DecisionDefinitionEngineDto deploySimpleOutputDecisionDefinition(final String outputClauseId,
                                                                             final String camInputVariable,
                                                                             final String ruleExpression,
                                                                             final DecisionTypeRef type) {
    final DmnModelGenerator dmnModelGenerator = DmnModelGenerator.create()
      .decision()
      .addInput("input", camInputVariable, type)
      .addOutput("output", outputClauseId, camInputVariable, type)
      .rule()
      .addStringInputEntry(type == DecisionTypeRef.STRING ? String.format("'%s'", ruleExpression) : ruleExpression)
      .addStringOutputEntry(camInputVariable)
      .buildRule()
      .buildDecision();
    return engineRule.deployDecisionDefinition(dmnModelGenerator.build());
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
                                                    final HashMap<String, InputVariableEntry> inputVariables) {
    engineRule.startDecisionInstance(
      id,
      inputVariables.entrySet().stream().collect(toMap(
        entry -> getInputVariableNameForId(entry.getKey()),
        entry -> entry.getValue().getValue()
      ))
    );
  }

  protected AuthorizedDecisionReportEvaluationResultDto<DecisionReportMapResultDto> evaluateMapReport(DecisionReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<AuthorizedDecisionReportEvaluationResultDto<DecisionReportMapResultDto>>() {});
      // @formatter:on
  }

  protected AuthorizedDecisionReportEvaluationResultDto<DecisionReportNumberResultDto> evaluateNumberReport(DecisionReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<AuthorizedDecisionReportEvaluationResultDto<DecisionReportNumberResultDto>>() {});
      // @formatter:on
  }

  protected AuthorizedDecisionReportEvaluationResultDto<RawDataDecisionReportResultDto> evaluateRawReport(DecisionReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<AuthorizedDecisionReportEvaluationResultDto<RawDataDecisionReportResultDto>>() {});
      // @formatter:on
  }

  protected Response evaluateReportAndReturnResponse(DecisionReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();
  }
}
