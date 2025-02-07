/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.zeebe;

import io.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import io.camunda.optimize.dto.optimize.importing.InputInstanceDto;
import io.camunda.optimize.dto.optimize.importing.OutputInstanceDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.dto.zeebe.decision.ZeebeDecisionInstanceDataDto;
import io.camunda.optimize.dto.zeebe.decision.ZeebeDecisionInstanceDataDto.EvaluatedDecision;
import io.camunda.optimize.dto.zeebe.decision.ZeebeDecisionInstanceDataDto.EvaluatedDecision.EvaluatedInput;
import io.camunda.optimize.dto.zeebe.decision.ZeebeDecisionInstanceDataDto.EvaluatedDecision.MatchedRule;
import io.camunda.optimize.dto.zeebe.decision.ZeebeDecisionInstanceDataDto.EvaluatedDecision.MatchedRule.EvaluatedOutput;
import io.camunda.optimize.dto.zeebe.decision.ZeebeDecisionInstanceRecordDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.DecisionInstanceWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.importing.DatabaseImportJobExecutor;
import io.camunda.optimize.service.importing.engine.service.ImportService;
import io.camunda.optimize.service.importing.job.DecisionInstanceDatabaseImportJob;
import io.camunda.optimize.service.util.IdGenerator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public class ZeebeDecisionInstanceImportService
    implements ImportService<ZeebeDecisionInstanceRecordDto> {

  public static final Set<DecisionEvaluationIntent> INTENTS_TO_IMPORT =
      Set.of(DecisionEvaluationIntent.EVALUATED);
  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ZeebeDecisionInstanceImportService.class);

  private final DatabaseImportJobExecutor databaseImportJobExecutor;
  private final ConfigurationService configurationService;
  private final DecisionInstanceWriter decisionInstanceWriter;
  private final int partitionId;
  private final DatabaseClient databaseClient;

  public ZeebeDecisionInstanceImportService(
      final ConfigurationService configurationService,
      final DecisionInstanceWriter decisionInstanceWriter,
      final int partitionId,
      // final ProcessDefinitionReader processDefinitionReader,
      final DatabaseClient databaseClient) {
    databaseImportJobExecutor =
        new DatabaseImportJobExecutor(getClass().getSimpleName(), configurationService);
    this.configurationService = configurationService;
    this.decisionInstanceWriter = decisionInstanceWriter;
    this.partitionId = partitionId;
    this.databaseClient = databaseClient;
  }

  @Override
  public void executeImport(
      final List<ZeebeDecisionInstanceRecordDto> pageOfDecisionInstancess,
      final Runnable importCompleteCallback) {
    LOG.trace("Importing decision instances from zeebe records...");

    final boolean newDataIsAvailable = !pageOfDecisionInstancess.isEmpty();
    if (newDataIsAvailable) {
      final List<DecisionInstanceDto> newOptimizeEntities =
          filterAndMapZeebeRecordsToOptimizeEntities(pageOfDecisionInstancess);
      final DatabaseImportJob<DecisionInstanceDto> databaseImportJob =
          createDatabaseImportJob(newOptimizeEntities, importCompleteCallback);
      addDatabaseImportJobToQueue(databaseImportJob);
    }
  }

  @Override
  public DatabaseImportJobExecutor getDatabaseImportJobExecutor() {
    return databaseImportJobExecutor;
  }

  private List<DecisionInstanceDto> filterAndMapZeebeRecordsToOptimizeEntities(
      final List<ZeebeDecisionInstanceRecordDto> zeebeRecords) {
    final List<DecisionInstanceDto> optimizeDtos =
        zeebeRecords.stream()
            .filter(zeebeRecord -> INTENTS_TO_IMPORT.contains(zeebeRecord.getIntent()))
            .map(this::mapZeebeRecordsToOptimizeEntities)
            .collect(Collectors.toList());
    LOG.debug(
        "Processing {} fetched zeebe decision instance records, of which {} are relevant to Optimize and will be imported.",
        zeebeRecords.size(),
        optimizeDtos.size());
    return optimizeDtos;
  }

  private DecisionInstanceDto mapZeebeRecordsToOptimizeEntities(
      final ZeebeDecisionInstanceRecordDto zeebeDecisionInstanceRecord) {
    final ZeebeDecisionInstanceDataDto recordData = zeebeDecisionInstanceRecord.getValue();
    LOG.error("====ZeebeDecisionInstanceDataDto: {}", recordData);
    final DecisionInstanceDto instanceDto = new DecisionInstanceDto();
    instanceDto.setDecisionInstanceId(String.valueOf(recordData.getDecisionKey()));
    instanceDto.setProcessDefinitionId(String.valueOf(recordData.getProcessDefinitionKey()));
    instanceDto.setProcessDefinitionKey(recordData.getBpmnProcessId());
    instanceDto.setDecisionDefinitionId(String.valueOf(recordData.getDecisionRequirementsKey()));
    instanceDto.setDecisionDefinitionKey(recordData.getDecisionRequirementsId());
    instanceDto.setDecisionDefinitionVersion(String.valueOf(recordData.getDecisionVersion()));
    instanceDto.setEvaluationDateTime(zeebeDecisionInstanceRecord.getDateForTimestamp());
    instanceDto.setProcessInstanceId(String.valueOf(recordData.getProcessInstanceKey()));
    // test.setRootProcessInstanceId();
    instanceDto.setActivityId(recordData.getElementId());
    // test.setCollectResultValue();
    // test.setRootDecisionInstanceId();
    instanceDto.setEngine(configurationService.getConfiguredZeebe().getName());
    instanceDto.setTenantId(recordData.getTenantId());

    final List<InputInstanceDto> inputs = new ArrayList<>();
    final List<OutputInstanceDto> outputs = new ArrayList<>();
    final Set<String> matchedRuleIds = new HashSet<>();
    for (final EvaluatedDecision evaluatedDecision : recordData.getEvaluatedDecisions()) {
      final String decisionId = evaluatedDecision.getDecisionId();
      for (final EvaluatedInput evaluatedInput : evaluatedDecision.getEvaluatedInputs()) {
        final InputInstanceDto inputInstanceDto =
            new InputInstanceDto(
                IdGenerator.getNextId(),
                evaluatedInput.getInputId(),
                evaluatedInput.getInputName(),
                VariableType.OBJECT,
                evaluatedInput.getInputValue());
        inputs.add(inputInstanceDto);
      }

      for (final MatchedRule matchedRule : evaluatedDecision.getMatchedRules()) {
        LOG.error("====MATCHED RULE: {}", matchedRule);
        matchedRuleIds.add(matchedRule.getRuleId());
        for (final EvaluatedOutput evaluatedOutput : matchedRule.getEvaluatedOutputs()) {
          final OutputInstanceDto output =
              new OutputInstanceDto(
                  IdGenerator.getNextId(),
                  evaluatedOutput.getOutputId(),
                  evaluatedOutput.getOutputName(),
                  matchedRule.getRuleId(),
                  matchedRule.getRuleIndex(),
                  evaluatedOutput.getOutputName(),
                  VariableType.OBJECT,
                  evaluatedOutput.getOutputValue());
          outputs.add(output);
        }
      }
    }
    if (!inputs.isEmpty()) {
      instanceDto.setInputs(inputs);
    }
    if (!outputs.isEmpty()) {
      instanceDto.setOutputs(outputs);
    }
    if (!matchedRuleIds.isEmpty()) {
      instanceDto.setMatchedRules(matchedRuleIds);
    }
    return instanceDto;
  }

  private DatabaseImportJob<DecisionInstanceDto> createDatabaseImportJob(
      final List<DecisionInstanceDto> instanceDefinitions, final Runnable importCompleteCallback) {
    final DecisionInstanceDatabaseImportJob decInstantImportJob =
        new DecisionInstanceDatabaseImportJob(
            decisionInstanceWriter, importCompleteCallback, databaseClient);
    decInstantImportJob.setEntitiesToImport(instanceDefinitions);
    return decInstantImportJob;
  }

  private void addDatabaseImportJobToQueue(
      final DatabaseImportJob<DecisionInstanceDto> databaseImportJob) {
    databaseImportJobExecutor.executeImportJob(databaseImportJob);
  }
}
