/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.zeebe;

import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_TERMINATED;

import io.camunda.optimize.dto.optimize.FlatProcessInstanceDto;
import io.camunda.optimize.dto.optimize.ProcessInstanceConstants;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import io.camunda.optimize.dto.optimize.query.process.FlatFlowNodeInstanceDto;
import io.camunda.optimize.dto.optimize.query.process.FlowNodeInstanceDto;
import io.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceDataDto;
import io.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceRecordDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.FlowNodeInstanceWriter;
import io.camunda.optimize.service.db.writer.ProcessInstanceWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.importing.DatabaseImportJobExecutor;
import io.camunda.optimize.service.importing.engine.service.ImportService;
import io.camunda.optimize.service.importing.job.ZeebeProcessInstanceImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public class ZeebeProcessInstanceImportService
    implements ImportService<ZeebeProcessInstanceRecordDto> {

  public static final Set<ProcessInstanceIntent> INTENTS_TO_IMPORT =
      Set.of(
          ProcessInstanceIntent.ELEMENT_COMPLETED,
          ProcessInstanceIntent.ELEMENT_TERMINATED,
          ProcessInstanceIntent.ELEMENT_ACTIVATING);
  private static final Set<BpmnElementType> TYPES_TO_IGNORE =
      Set.of(BpmnElementType.UNSPECIFIED, BpmnElementType.SEQUENCE_FLOW);
  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ZeebeProcessInstanceImportService.class);

  private final DatabaseImportJobExecutor databaseImportJobExecutor;
  private final ConfigurationService configurationService;
  private final ProcessInstanceWriter processInstanceWriter;
  private final FlowNodeInstanceWriter flowNodeInstanceWriter;
  private final DatabaseClient databaseClient;
  private final int partitionId;

  public ZeebeProcessInstanceImportService(
      final ConfigurationService configurationService,
      final ProcessInstanceWriter processInstanceWriter,
      final FlowNodeInstanceWriter flowNodeInstanceWriter,
      final int partitionId,
      final DatabaseClient databaseClient) {
    databaseImportJobExecutor =
        new DatabaseImportJobExecutor(getClass().getSimpleName(), configurationService);
    this.configurationService = configurationService;
    this.processInstanceWriter = processInstanceWriter;
    this.flowNodeInstanceWriter = flowNodeInstanceWriter;
    this.partitionId = partitionId;
    this.databaseClient = databaseClient;
  }

  @Override
  public void executeImport(
      final List<ZeebeProcessInstanceRecordDto> zeebeRecords,
      final Runnable importCompleteCallback) {
    if (zeebeRecords.isEmpty()) {
      importCompleteCallback.run();
      return;
    }
    final TransformedProcessInstanceData transformed = transformRecords(zeebeRecords);
    if (transformed.flatProcessInstances().isEmpty() && transformed.flowNodeInstances().isEmpty()) {
      importCompleteCallback.run();
      return;
    }
    final ZeebeProcessInstanceImportJob job = buildImportJob(transformed, importCompleteCallback);
    databaseImportJobExecutor.executeImportJob(job);
  }

  @Override
  public DatabaseImportJobExecutor getDatabaseImportJobExecutor() {
    return databaseImportJobExecutor;
  }

  /**
   * Creates (but does not execute) a {@link ZeebeProcessInstanceImportJob} for the given records.
   *
   * @return an {@link Optional} containing the prepared import job, or empty if there are no
   *     relevant records to import.
   */
  public Optional<ZeebeProcessInstanceImportJob> createImportJob(
      final List<ZeebeProcessInstanceRecordDto> zeebeRecords) {
    if (zeebeRecords.isEmpty()) {
      return Optional.empty();
    }
    final TransformedProcessInstanceData transformed = transformRecords(zeebeRecords);
    if (transformed.flatProcessInstances().isEmpty() && transformed.flowNodeInstances().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(buildImportJob(transformed, () -> {}));
  }

  /**
   * Transforms the raw zeebe records into the typed DTOs needed for import. The result holds
   * process instances, flat process instances, and flat flow node instances.
   */
  private TransformedProcessInstanceData transformRecords(
      final List<ZeebeProcessInstanceRecordDto> zeebeRecords) {
    final List<ZeebeProcessInstanceRecordDto> filteredRecords =
        zeebeRecords.stream()
            .filter(
                zeebeRecord -> {
                  final BpmnElementType bpmnElementType =
                      zeebeRecord.getValue().getBpmnElementType();
                  return bpmnElementType != null && !TYPES_TO_IGNORE.contains(bpmnElementType);
                })
            .filter(zeebeRecord -> INTENTS_TO_IMPORT.contains(zeebeRecord.getIntent()))
            .collect(Collectors.toList());
    final Map<Long, List<ZeebeProcessInstanceRecordDto>> byProcessInstanceKey =
        filteredRecords.stream()
            .collect(Collectors.groupingBy(r -> r.getValue().getProcessInstanceKey()));
    final List<FlatProcessInstanceDto> flatProcessInstances =
        byProcessInstanceKey.values().stream()
            .map(
                records -> {
                  final ProcessInstanceDto pi = createProcessInstanceForData(records);
                  if (!pi.getFlowNodeInstances().isEmpty()
                      || pi.getStartDate() != null
                      || pi.getEndDate() != null) {
                    final FlatProcessInstanceDto flat = FlatProcessInstanceDto.from(pi);
                    flat.setOrdinal(records.get(0).getValue().getOrdinal());
                    return flat;
                  }
                  return null;
                })
            .filter(flat -> flat != null)
            .collect(Collectors.toList());
    final List<FlatFlowNodeInstanceDto> flowNodeInstances =
        byProcessInstanceKey.values().stream()
            .flatMap(records -> createFlatFlowNodeInstancesForData(records).stream())
            .collect(Collectors.toList());
    LOG.debug(
        "Processing {} fetched process instances, {} flat process instances, {} flow node instances.",
        zeebeRecords.size(),
        flatProcessInstances.size(),
        flowNodeInstances.size());
    return new TransformedProcessInstanceData(flatProcessInstances, flowNodeInstances);
  }

  private ZeebeProcessInstanceImportJob buildImportJob(
      final TransformedProcessInstanceData transformed, final Runnable importCompleteCallback) {
    final ZeebeProcessInstanceImportJob job =
        new ZeebeProcessInstanceImportJob(
            processInstanceWriter,
            flowNodeInstanceWriter,
            configurationService,
            importCompleteCallback,
            databaseClient,
            ZEEBE_PROCESS_INSTANCE_INDEX_NAME);
    job.setFlatProcessInstances(transformed.flatProcessInstances());
    job.setFlowNodeInstances(transformed.flowNodeInstances());
    return job;
  }

  private ProcessInstanceDto createProcessInstanceForData(
      final List<ZeebeProcessInstanceRecordDto> recordsForInstance) {
    final ZeebeProcessInstanceRecordDto firstRecord = recordsForInstance.get(0);
    final ZeebeProcessInstanceDataDto firstRecordValue = firstRecord.getValue();
    final ProcessInstanceDto instanceToAdd = createSkeletonProcessInstance(firstRecordValue);
    instanceToAdd.setProcessDefinitionVersion(String.valueOf(firstRecordValue.getVersion()));
    instanceToAdd.setIncidents(Collections.emptyList());
    instanceToAdd.setVariables(Collections.emptyList());
    // Flow node instances are stored separately in the flat flow node instance index;
    // the process instance document does not embed them.
    instanceToAdd.setFlowNodeInstances(Collections.emptyList());
    updateProcessStateAndDateProperties(instanceToAdd, recordsForInstance);
    return instanceToAdd;
  }

  private List<FlatFlowNodeInstanceDto> createFlatFlowNodeInstancesForData(
      final List<ZeebeProcessInstanceRecordDto> recordsForInstance) {
    final ZeebeProcessInstanceDataDto firstRecordValue = recordsForInstance.get(0).getValue();
    final String processDefinitionKey = firstRecordValue.getBpmnProcessId();
    final String processDefinitionVersion = String.valueOf(firstRecordValue.getVersion());
    final String processDefinitionId = String.valueOf(firstRecordValue.getProcessDefinitionKey());
    final String processInstanceId = String.valueOf(firstRecordValue.getProcessInstanceKey());
    final int ordinal = firstRecordValue.getOrdinal();

    final Map<Long, FlowNodeInstanceDto> flowNodeInstancesByRecordKey = new HashMap<>();
    final Map<Long, Boolean> hasActivatingByRecordKey = new HashMap<>();

    recordsForInstance.stream()
        .filter(
            zeebeRecord ->
                zeebeRecord.getValue().getBpmnElementType().getElementTypeName().isPresent())
        .filter(
            zeebeRecord ->
                !BpmnElementType.PROCESS.equals(zeebeRecord.getValue().getBpmnElementType()))
        .forEach(
            zeebeFlowNodeInstanceRecord -> {
              final long recordKey = zeebeFlowNodeInstanceRecord.getKey();
              final FlowNodeInstanceDto flowNodeForKey =
                  flowNodeInstancesByRecordKey.getOrDefault(
                      recordKey, createSkeletonFlowNodeInstance(zeebeFlowNodeInstanceRecord));
              final ProcessInstanceIntent instanceIntent = zeebeFlowNodeInstanceRecord.getIntent();
              if (instanceIntent == ELEMENT_COMPLETED) {
                flowNodeForKey.setEndDate(zeebeFlowNodeInstanceRecord.getDateForTimestamp());
              } else if (instanceIntent == ELEMENT_TERMINATED) {
                flowNodeForKey.setCanceled(true);
                flowNodeForKey.setEndDate(zeebeFlowNodeInstanceRecord.getDateForTimestamp());
              } else if (instanceIntent == ELEMENT_ACTIVATING) {
                flowNodeForKey.setStartDate(zeebeFlowNodeInstanceRecord.getDateForTimestamp());
                hasActivatingByRecordKey.put(recordKey, true);
              }
              updateDurationIfCompleted(flowNodeForKey);
              flowNodeInstancesByRecordKey.put(recordKey, flowNodeForKey);
            });

    return flowNodeInstancesByRecordKey.entrySet().stream()
        .map(
            entry -> {
              final long recordKey = entry.getKey();
              final FlowNodeInstanceDto flowNode = entry.getValue();
              final FlatFlowNodeInstanceDto dto =
                  FlatFlowNodeInstanceDto.fromProcessInstanceAndFlowNode(
                      processDefinitionKey,
                      processDefinitionVersion,
                      processDefinitionId,
                      processInstanceId,
                      flowNode);
              dto.setPartition(partitionId);
              dto.setOrdinal(ordinal);
              // If the batch contains an ACTIVATING event for this flow node, treat it as new
              // (full INDEX); otherwise use UPDATE+docs for completion/termination only.
              dto.setNew(hasActivatingByRecordKey.getOrDefault(recordKey, false));
              return dto;
            })
        .collect(Collectors.toList());
  }

  private ProcessInstanceDto createSkeletonProcessInstance(final ZeebeProcessInstanceDataDto data) {
    final ProcessInstanceDto processInstanceDto = new ProcessInstanceDto();
    processInstanceDto.setProcessDefinitionKey(data.getBpmnProcessId());
    processInstanceDto.setProcessInstanceId(String.valueOf(data.getProcessInstanceKey()));
    processInstanceDto.setProcessDefinitionId(String.valueOf(data.getProcessDefinitionKey()));
    processInstanceDto.setTenantId(data.getTenantId());
    processInstanceDto.setPartition(partitionId);
    processInstanceDto.setDataSource(
        new ZeebeDataSourceDto(configurationService.getConfiguredZeebe().getName(), partitionId));
    return processInstanceDto;
  }

  private FlowNodeInstanceDto createSkeletonFlowNodeInstance(
      final ZeebeProcessInstanceRecordDto zeebeProcessInstanceRecordDto) {
    final ZeebeProcessInstanceDataDto zeebeInstanceRecord =
        zeebeProcessInstanceRecordDto.getValue();
    final FlowNodeInstanceDto flowNodeInstanceDto =
        new FlowNodeInstanceDto(
            zeebeInstanceRecord.getBpmnProcessId(),
            String.valueOf(zeebeInstanceRecord.getVersion()),
            zeebeInstanceRecord.getTenantId(),
            String.valueOf(zeebeInstanceRecord.getProcessInstanceKey()),
            zeebeInstanceRecord.getElementId(),
            zeebeInstanceRecord
                .getBpmnElementType()
                .getElementTypeName()
                .orElseThrow(
                    () ->
                        new OptimizeRuntimeException(
                            "Cannot create flow node instances for records without element types")),
            String.valueOf(zeebeProcessInstanceRecordDto.getKey()));
    flowNodeInstanceDto.setCanceled(false);
    return flowNodeInstanceDto;
  }

  private void updateProcessStateAndDateProperties(
      final ProcessInstanceDto instanceToAdd,
      final List<ZeebeProcessInstanceRecordDto> recordsForInstance) {
    recordsForInstance.stream()
        .filter(
            zeebeRecord ->
                BpmnElementType.PROCESS.equals(zeebeRecord.getValue().getBpmnElementType()))
        .forEach(
            processInstance -> {
              switch (processInstance.getIntent()) {
                case ELEMENT_COMPLETED:
                  updateStateIfValidTransition(
                      instanceToAdd, ProcessInstanceConstants.COMPLETED_STATE);
                  instanceToAdd.setEndDate(processInstance.getDateForTimestamp());
                  break;
                case ELEMENT_TERMINATED:
                  updateStateIfValidTransition(
                      instanceToAdd, ProcessInstanceConstants.EXTERNALLY_TERMINATED_STATE);
                  instanceToAdd.setEndDate(processInstance.getDateForTimestamp());
                  break;
                case ELEMENT_ACTIVATING:
                  updateStateIfValidTransition(
                      instanceToAdd, ProcessInstanceConstants.ACTIVE_STATE);
                  instanceToAdd.setStartDate(processInstance.getDateForTimestamp());
                  break;
                default:
                  throw new OptimizeRuntimeException(
                      "Unsupported intent: " + processInstance.getIntent());
              }
              updateDurationIfCompleted(instanceToAdd);
            });
  }

  private void updateStateIfValidTransition(
      final ProcessInstanceDto instance, final String targetState) {
    if (instance.getState() == null
        || instance.getState().equals(ProcessInstanceConstants.ACTIVE_STATE)) {
      instance.setState(targetState);
    }
  }

  private void updateDurationIfCompleted(final ProcessInstanceDto instanceToAdd) {
    if (instanceToAdd.getStartDate() != null && instanceToAdd.getEndDate() != null) {
      instanceToAdd.setDuration(
          instanceToAdd.getStartDate().until(instanceToAdd.getEndDate(), ChronoUnit.MILLIS));
    }
  }

  private void updateDurationIfCompleted(final FlowNodeInstanceDto flowNodeToAdd) {
    if (flowNodeToAdd.getStartDate() != null && flowNodeToAdd.getEndDate() != null) {
      flowNodeToAdd.setTotalDurationInMs(
          flowNodeToAdd.getStartDate().until(flowNodeToAdd.getEndDate(), ChronoUnit.MILLIS));
    }
  }

  /** Holds the intermediate DTOs produced by record transformation. */
  private record TransformedProcessInstanceData(
      List<FlatProcessInstanceDto> flatProcessInstances,
      List<FlatFlowNodeInstanceDto> flowNodeInstances) {}
}
