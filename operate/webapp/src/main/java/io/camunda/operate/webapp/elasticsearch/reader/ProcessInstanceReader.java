/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessInstanceReader.class);

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
      final PermissionsService.ResourcesAllowed allowed =
          permissionsService.getProcessesWithPermission(IdentityPermission.READ);
      statistics =
          processStore.getCoreStatistics(
              (allowed == null || allowed.isAll()) ? null : allowed.getIds());
    } else {
      statistics = processStore.getCoreStatistics(null);
    }
    final Long runningCount = statistics.get("running");
    final Long incidentCount = statistics.get("incidents");
    final ProcessInstanceCoreStatisticsDto processInstanceCoreStatisticsDto =
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
