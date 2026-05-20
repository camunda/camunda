/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.db.rdbms.write.domain.AgentInstanceDbModel;
import io.camunda.db.rdbms.write.domain.AgentInstanceDbModel.AgentInstanceStatus;
import io.camunda.db.rdbms.write.domain.AgentInstanceDbModel.Builder;
import io.camunda.db.rdbms.write.service.AgentInstanceWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.exporter.rdbms.utils.DateUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentInstanceRecordValue;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentInstanceExportHandler implements RdbmsExportHandler<AgentInstanceRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AgentInstanceExportHandler.class);

  private static final Set<AgentInstanceIntent> EXPORTABLE_INTENTS =
      Set.of(
          AgentInstanceIntent.CREATED, AgentInstanceIntent.UPDATED, AgentInstanceIntent.COMPLETED);

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final AgentInstanceWriter writer;

  public AgentInstanceExportHandler(final AgentInstanceWriter writer) {
    this.writer = writer;
  }

  @Override
  public boolean canExport(final Record<AgentInstanceRecordValue> record) {
    return record.getIntent() instanceof final AgentInstanceIntent intent
        && EXPORTABLE_INTENTS.contains(intent);
  }

  @Override
  public void export(final Record<AgentInstanceRecordValue> record) {
    final AgentInstanceIntent intent = (AgentInstanceIntent) record.getIntent();
    if (intent == AgentInstanceIntent.CREATED) {
      writer.create(mapForCreate(record));
    } else {
      writer.update(mapForUpdate(record, intent));
    }
  }

  private AgentInstanceDbModel mapForCreate(final Record<AgentInstanceRecordValue> record) {
    final AgentInstanceRecordValue value = record.getValue();
    final var timestamp = DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp()));

    return new Builder()
        .agentInstanceKey(record.getKey())
        .elementId(value.getElementId())
        .processInstanceKey(value.getProcessInstanceKey())
        // `rootProcessInstanceKey` is not yet available on the record — use -1 sentinel
        // until https://github.com/camunda/camunda/issues/53236 lands, matching PR-2.
        .rootProcessInstanceKey(-1L)
        .bpmnProcessId(value.getBpmnProcessId())
        .processDefinitionKey(value.getProcessDefinitionKey())
        .processDefinitionVersion(value.getProcessDefinitionVersion())
        .versionTag(value.getVersionTag())
        .tenantId(value.getTenantId())
        .partitionId(record.getPartitionId())
        .status(mapStatus(value.getStatus()))
        .model(value.getDefinition().getModel())
        .provider(value.getDefinition().getProvider())
        .systemPrompt(value.getDefinition().getSystemPrompt())
        .maxTokens(value.getLimits().getMaxTokens())
        .maxModelCalls(value.getLimits().getMaxModelCalls())
        .maxToolCalls(value.getLimits().getMaxToolCalls())
        .inputTokens(value.getMetrics().getInputTokens())
        .outputTokens(value.getMetrics().getOutputTokens())
        .modelCalls(value.getMetrics().getModelCalls())
        .toolCalls(value.getMetrics().getToolCalls())
        .tools(serializeTools(value.getTools()))
        .creationDate(timestamp)
        .lastUpdatedDate(timestamp)
        .elementInstanceKeys(List.of(value.getElementInstanceKey()))
        .build();
  }

  private AgentInstanceDbModel mapForUpdate(
      final Record<AgentInstanceRecordValue> record, final AgentInstanceIntent intent) {
    final AgentInstanceRecordValue value = record.getValue();
    final var timestamp = DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp()));

    final Builder builder =
        new Builder()
            .agentInstanceKey(record.getKey())
            .status(mapStatus(value.getStatus()))
            .inputTokens(value.getMetrics().getInputTokens())
            .outputTokens(value.getMetrics().getOutputTokens())
            .modelCalls(value.getMetrics().getModelCalls())
            .toolCalls(value.getMetrics().getToolCalls())
            .tools(serializeTools(value.getTools()))
            .lastUpdatedDate(timestamp)
            .elementInstanceKeys(List.of(value.getElementInstanceKey()));

    if (intent == AgentInstanceIntent.COMPLETED) {
      builder.completionDate(timestamp);
    }

    return builder.build();
  }

  private static AgentInstanceStatus mapStatus(
      final io.camunda.zeebe.protocol.record.value.AgentInstanceStatus protocolStatus) {
    return switch (protocolStatus) {
      case INITIALIZING -> AgentInstanceStatus.INITIALIZING;
      case IDLE -> AgentInstanceStatus.IDLE;
      case THINKING -> AgentInstanceStatus.THINKING;
      case TOOL_CALLING -> AgentInstanceStatus.TOOL_CALLING;
      case TOOL_DISCOVERY -> AgentInstanceStatus.TOOL_DISCOVERY;
      case COMPLETED -> AgentInstanceStatus.COMPLETED;
      default -> {
        LOGGER.warn(
            "Received unexpected AgentInstanceStatus: {}, will be mapped to UNKNOWN",
            protocolStatus);
        yield AgentInstanceStatus.UNKNOWN;
      }
    };
  }

  private static String serializeTools(
      final List<? extends AgentInstanceRecordValue.AgentInstanceToolValue> tools) {
    if (tools == null || tools.isEmpty()) {
      return null;
    }
    final var toolMaps =
        tools.stream()
            .map(
                t -> {
                  final Map<String, Object> m = new HashMap<>();
                  m.put("name", t.getName());
                  m.put("description", t.getDescription());
                  m.put("elementId", t.getElementId());
                  return m;
                })
            .toList();
    try {
      return MAPPER.writeValueAsString(toolMaps);
    } catch (final JsonProcessingException e) {
      LOGGER.error("Failed to serialize agent instance tools", e);
      return null;
    }
  }
}
