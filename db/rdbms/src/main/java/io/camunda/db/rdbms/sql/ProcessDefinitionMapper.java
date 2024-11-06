/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.ProcessDefinitionDbQuery;
import io.camunda.db.rdbms.write.domain.ProcessDefinitionDbModel;
import io.camunda.search.entities.ProcessDefinitionEntity;
import java.util.List;
import java.util.function.Function;

public interface ProcessDefinitionMapper {

  void insert(ProcessDefinitionDbModel processDeployment);

  Long count(ProcessDefinitionDbQuery filter);

  List<ProcessDefinitionEntity> search(ProcessDefinitionDbQuery filter);

  enum ProcessDefinitionSearchColumn implements SearchColumn<ProcessDefinitionEntity> {
    PROCESS_DEFINITION_KEY("key", ProcessDefinitionEntity::key),
    PROCESS_DEFINITION_ID("bpmnProcessId", ProcessDefinitionEntity::bpmnProcessId),
    NAME("name", ProcessDefinitionEntity::name),
    VERSION("version", ProcessDefinitionEntity::version),
    VERSION_TAG("versionTag", ProcessDefinitionEntity::versionTag),
    TENANT_ID("tenantId", ProcessDefinitionEntity::tenantId),
    FORM_ID("formId", ProcessDefinitionEntity::formId),
    RESOURCE_NAME("resourceName", ProcessDefinitionEntity::resourceName),
    BPMN_XML("bpmnXml", ProcessDefinitionEntity::bpmnXml);

    private final String property;
    private final Function<ProcessDefinitionEntity, Object> propertyReader;
    private final Function<Object, Object> sortOptionConverter;

    ProcessDefinitionSearchColumn(
        final String property, final Function<ProcessDefinitionEntity, Object> propertyReader) {
      this(property, propertyReader, Function.identity());
    }

    ProcessDefinitionSearchColumn(
        final String property,
        final Function<ProcessDefinitionEntity, Object> propertyReader,
        final Function<Object, Object> sortOptionConverter) {
      this.property = property;
      this.propertyReader = propertyReader;
      this.sortOptionConverter = sortOptionConverter;
    }

    @Override
    public Object getPropertyValue(final ProcessDefinitionEntity entity) {
      return propertyReader.apply(entity);
    }

    @Override
    public Object convertSortOption(final Object object) {
      if (object == null) {
        return null;
      }

      return sortOptionConverter.apply(object);
    }

    public static ProcessDefinitionSearchColumn findByProperty(final String property) {
      for (final ProcessDefinitionSearchColumn column : ProcessDefinitionSearchColumn.values()) {
        if (column.property.equals(property)) {
          return column;
        }
      }

      return null;
    }
  }
}
