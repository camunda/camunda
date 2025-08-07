/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.reader;

import io.camunda.operate.store.ProcessStore;
import io.camunda.operate.webapp.rest.dto.ProcessRequestDto;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ProcessReader {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(io.camunda.operate.webapp.reader.ProcessReader.class);

  private final ProcessStore processStore;

  private final PermissionsService permissionsService;

  public ProcessReader(
      final ProcessStore processStore, final PermissionsService permissionsService) {
    this.processStore = processStore;
    this.permissionsService = permissionsService;
  }

  /**
   * Gets the process diagram XML as a string.
   *
   * @param processDefinitionKey
   * @return
   */
  public String getDiagram(final Long processDefinitionKey) {
    return processStore.getDiagramByKey(processDefinitionKey);
  }

  /**
   * Gets the process by id.
   *
   * @param processDefinitionKey
   * @return
   */
  public ProcessEntity getProcess(final Long processDefinitionKey) {
    return processStore.getProcessByKey(processDefinitionKey);
  }

  /**
   * Returns map of Process entities grouped by bpmnProcessId.
   *
   * @return
   */
  public Map<ProcessStore.ProcessKey, List<ProcessEntity>> getProcessesGrouped(
      final ProcessRequestDto request) {
    return processStore.getProcessesGrouped(
        request.getTenantId(), getAllowedProcessIdsOrNullForAll());
  }

  /**
   * Returns up to maxSize ProcessEntities only filled with the given field names.
   *
   * @return Map of id -> ProcessEntity
   */
  public Map<Long, ProcessEntity> getProcessesWithFields(
      final int maxSize, final String... fields) {
    return processStore.getProcessesIdsToProcessesWithFields(
        getAllowedProcessIdsOrNullForAll(), maxSize, fields);
  }

  /**
   * Returns up to 1000 ProcessEntities only filled with the given field names.
   *
   * @return Map of id -> ProcessEntity
   */
  public Map<Long, ProcessEntity> getProcessesWithFields(final String... fields) {
    return getProcessesWithFields(1000, fields);
  }

  private Set<String> getAllowedProcessIdsOrNullForAll() {
    final PermissionsService.ResourcesAllowed allowed =
        permissionsService.getProcessesWithPermission(PermissionType.READ_PROCESS_DEFINITION);
    return allowed.isAll() ? null : allowed.getIds();
  }
}
