/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.ProcessStore;
import io.camunda.operate.util.TreePath;
import io.camunda.operate.webapp.reader.OperationReader;
import io.camunda.operate.webapp.rest.dto.ProcessInstanceCoreStatisticsDto;
import io.camunda.operate.webapp.rest.dto.ProcessInstanceReferenceDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProcessInstanceReader {

  private static final Logger logger = LoggerFactory.getLogger(ProcessInstanceReader.class);

  @Autowired protected ObjectMapper objectMapper;
  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private ProcessStore processStore;

  @Autowired private OperationReader operationReader;

  @Autowired(required = false)
  private PermissionsService permissionsService;

  /**
   * Searches for process instance by key.
   *
   * @param processInstanceKey
   * @return
   */
  public ListViewProcessInstanceDto getProcessInstanceWithOperationsByKey(Long processInstanceKey) {
    final ProcessInstanceForListViewEntity processInstance =
        processStore.getProcessInstanceListViewByKey(processInstanceKey);

    final List<ProcessInstanceReferenceDto> callHierarchy =
        createCallHierarchy(processInstance.getTreePath(), String.valueOf(processInstanceKey));

    return ListViewProcessInstanceDto.createFrom(
        processInstance,
        operationReader.getOperationsByProcessInstanceKey(processInstanceKey),
        callHierarchy,
        permissionsService,
        objectMapper);
  }

  private List<ProcessInstanceReferenceDto> createCallHierarchy(
      final String treePath, final String currentProcessInstanceId) {
    final List<ProcessInstanceReferenceDto> callHierarchy = new ArrayList<>();
    final List<String> processInstanceIds = new TreePath(treePath).extractProcessInstanceIds();
    return processStore
        .createCallHierarchyFor(processInstanceIds, currentProcessInstanceId)
        .stream()
        .map(
            r ->
                new ProcessInstanceReferenceDto()
                    .setInstanceId(String.valueOf(r.get("instanceId")))
                    .setProcessDefinitionId(r.get("processDefinitionId"))
                    .setProcessDefinitionName(r.get("processDefinitionName")))
        .sorted(Comparator.comparing(ref -> processInstanceIds.indexOf(ref.getInstanceId())))
        .toList();
  }

  /**
   * Searches for process instance by key.
   *
   * @param processInstanceKey
   * @return
   */
  public ProcessInstanceForListViewEntity getProcessInstanceByKey(Long processInstanceKey) {
    return processStore.getProcessInstanceListViewByKey(processInstanceKey);
  }

  public ProcessInstanceCoreStatisticsDto getCoreStatistics() {
    final Map<String, Long> statistics;
    if (permissionsService != null) {
      PermissionsService.ResourcesAllowed allowed =
          permissionsService.getProcessesWithPermission(IdentityPermission.READ);
      statistics =
          processStore.getCoreStatistics(
              (allowed == null || allowed.isAll()) ? null : allowed.getIds());
    } else {
      statistics = processStore.getCoreStatistics(null);
    }
    final Long runningCount = statistics.get("running");
    final Long incidentCount = statistics.get("incidents");
    ProcessInstanceCoreStatisticsDto processInstanceCoreStatisticsDto =
        new ProcessInstanceCoreStatisticsDto()
            .setRunning(runningCount)
            .setActive(runningCount - incidentCount)
            .setWithIncidents(incidentCount);
    return processInstanceCoreStatisticsDto;
  }

  public String getProcessInstanceTreePath(final String processInstanceId) {
    return processStore.getProcessInstanceTreePathById(processInstanceId);
  }
}
