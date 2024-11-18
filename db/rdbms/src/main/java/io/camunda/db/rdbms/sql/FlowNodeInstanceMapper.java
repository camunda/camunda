/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.FlowNodeInstanceDbQuery;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.zeebe.util.DateUtil;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Function;

public interface FlowNodeInstanceMapper {

  void insert(FlowNodeInstanceDbModel flowNode);

  void updateStateAndEndDate(EndFlowNodeDto dto);

  Long count(FlowNodeInstanceDbQuery filter);

  List<FlowNodeInstanceEntity> search(FlowNodeInstanceDbQuery filter);

  record EndFlowNodeDto(
      long flowNodeInstanceKey,
      FlowNodeInstanceEntity.FlowNodeState state,
      OffsetDateTime endDate) {}

  enum FlowNodeInstanceSearchColumn implements SearchColumn<FlowNodeInstanceEntity> {
    FLOW_NODE_INSTANCE_KEY("key", FlowNodeInstanceEntity::key),
    FLOW_NODE_ID("flowNodeId", FlowNodeInstanceEntity::flowNodeId),
    PROCESS_INSTANCE_KEY("processInstanceKey", FlowNodeInstanceEntity::processInstanceKey),
    PROCESS_DEFINITION_KEY("processDefinitionKey", FlowNodeInstanceEntity::processDefinitionKey),
    PROCESS_DEFINITION_ID("bpmnProcessId", FlowNodeInstanceEntity::bpmnProcessId),
    START_DATE("startDate", FlowNodeInstanceEntity::startDate, DateUtil::fuzzyToOffsetDateTime),
    END_DATE("endDate", FlowNodeInstanceEntity::endDate, DateUtil::fuzzyToOffsetDateTime),
    STATE("state", FlowNodeInstanceEntity::state),
    TYPE("type", FlowNodeInstanceEntity::type),
    TENANT_ID("tenantId", FlowNodeInstanceEntity::tenantId),
    TREE_PATH("treePath", FlowNodeInstanceEntity::treePath),
    SCOPE_KEY("scopeKey", FlowNodeInstanceEntity::scopeKey),
    INCIDENT_KEY("incidentKey", FlowNodeInstanceEntity::incidentKey),
    INCIDENT("incident", FlowNodeInstanceEntity::incident);

    private final String property;
    private final Function<FlowNodeInstanceEntity, Object> propertyReader;
    private final Function<Object, Object> sortOptionConverter;

    FlowNodeInstanceSearchColumn(
        final String property, final Function<FlowNodeInstanceEntity, Object> propertyReader) {
      this(property, propertyReader, Function.identity());
    }

    FlowNodeInstanceSearchColumn(
        final String property,
        final Function<FlowNodeInstanceEntity, Object> propertyReader,
        final Function<Object, Object> sortOptionConverter) {
      this.property = property;
      this.propertyReader = propertyReader;
      this.sortOptionConverter = sortOptionConverter;
    }

    @Override
    public Object getPropertyValue(final FlowNodeInstanceEntity entity) {
      return propertyReader.apply(entity);
    }

    @Override
    public Object convertSortOption(final Object object) {
      if (object == null) {
        return null;
      }

      return sortOptionConverter.apply(object);
    }

    public static FlowNodeInstanceMapper.FlowNodeInstanceSearchColumn findByProperty(
        final String property) {
      for (final FlowNodeInstanceMapper.FlowNodeInstanceSearchColumn column :
          FlowNodeInstanceMapper.FlowNodeInstanceSearchColumn.values()) {
        if (column.property.equals(property)) {
          return column;
        }
      }

      return null;
    }
  }
}
