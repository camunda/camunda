/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import io.camunda.operate.store.ProcessStore;
import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.webapp.rest.dto.ProcessRequestDto;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Profile("!opensearch")
@Component
public class ProcessReader implements io.camunda.operate.webapp.reader.ProcessReader {

  private static final Logger logger = LoggerFactory.getLogger(ProcessReader.class);

  @Autowired
  private ProcessStore processStore;

  @Autowired(required = false)
  private PermissionsService permissionsService;

  /**
   * Gets the process diagram XML as a string.
   * @param processDefinitionKey
   * @return
   */
  @Override
  public String getDiagram(Long processDefinitionKey) {
    return processStore.getDiagramByKey(processDefinitionKey);
  }

  /**
   * Gets the process by id.
   * @param processDefinitionKey
   * @return
   */
  @Override
  public ProcessEntity getProcess(Long processDefinitionKey) {
    return processStore.getProcessByKey(processDefinitionKey);
  }

  /**
   * Returns map of Process entities grouped by bpmnProcessId.
   * @return
   */
  @Override
  public Map<String, List<ProcessEntity>> getProcessesGrouped(ProcessRequestDto request) {
    return processStore.getProcessesGrouped(request.getTenantId(), getAllowedProcessIdsOrNullForAll());
  }

  /**
   * Returns map of Process entities by process ids.
   * @return
   */
  @Override
  public Map<Long, ProcessEntity> getProcesses() {
    return processStore.getProcessIdsToProcesses();
  }

  /**
   * Returns up to maxSize ProcessEntities only filled with the given field names.
   * @return Map of id -> ProcessEntity
   */
  @Override
  public Map<Long, ProcessEntity> getProcessesWithFields(int maxSize, String... fields) {
    return processStore.getProcessesIdsToProcessesWithFields(getAllowedProcessIdsOrNullForAll(), maxSize, fields);
  }

  /**
   * Returns up to 1000 ProcessEntities only filled with the given field names.
   * @return Map of id -> ProcessEntity
   */
  @Override
  public Map<Long, ProcessEntity> getProcessesWithFields(String... fields){
    return getProcessesWithFields(1000, fields);
  }

  private Set<String> getAllowedProcessIdsOrNullForAll() {
    if (permissionsService == null) return null;

    final PermissionsService.ResourcesAllowed allowed = permissionsService.getProcessesWithPermission(
          IdentityPermission.READ);
    return allowed == null || allowed.isAll() ? null : allowed.getIds();
  }

}
