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

import static io.camunda.operate.webapp.rest.dto.incidents.IncidentDto.FALLBACK_PROCESS_DEFINITION_NAME;

import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.entities.ErrorType;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.store.FlowNodeStore;
import io.camunda.operate.store.IncidentStore;
import io.camunda.operate.util.TreePath;
import io.camunda.operate.webapp.data.IncidentDataHolder;
import io.camunda.operate.webapp.reader.OperationReader;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentErrorTypeDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentFlowNodeDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentResponseDto;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class IncidentReader extends AbstractReader
    implements io.camunda.operate.webapp.reader.IncidentReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(IncidentReader.class);

  @Autowired private OperationReader operationReader;

  @Autowired private ProcessInstanceReader processInstanceReader;

  @Autowired private ProcessCache processCache;

  @Autowired private IncidentStore incidentStore;

  @Autowired private FlowNodeStore flowNodeStore;

  @Override
  public List<IncidentEntity> getAllIncidentsByProcessInstanceKey(Long processInstanceKey) {
    return incidentStore.getIncidentsByProcessInstanceKey(processInstanceKey);
  }

  /**
   * Returns map of incident ids per process instance id.
   *
   * @param processInstanceKeys
   * @return
   */
  @Override
  public Map<Long, List<Long>> getIncidentKeysPerProcessInstance(List<Long> processInstanceKeys) {
    return incidentStore.getIncidentKeysPerProcessInstance(processInstanceKeys);
  }

  @Override
  public IncidentEntity getIncidentById(Long incidentKey) {
    return incidentStore.getIncidentById(incidentKey);
  }

  @Override
  public IncidentResponseDto getIncidentsByProcessInstanceId(String processInstanceId) {
    // get treePath for process instance
    final String treePath = processInstanceReader.getProcessInstanceTreePath(processInstanceId);

    final List<Map<ErrorType, Long>> errorTypes = new ArrayList<>();
    final List<IncidentEntity> incidents =
        incidentStore.getIncidentsWithErrorTypesFor(treePath, errorTypes);

    final IncidentResponseDto incidentResponse = new IncidentResponseDto();
    incidentResponse.setErrorTypes(
        errorTypes.stream()
            .map(
                m -> {
                  final var entry = m.entrySet().iterator().next();
                  return IncidentErrorTypeDto.createFrom(entry.getKey())
                      .setCount(entry.getValue().intValue());
                })
            .collect(Collectors.toList()));

    final Map<Long, String> processNames = new HashMap<>();
    incidents.stream()
        .filter(inc -> processNames.get(inc.getProcessDefinitionKey()) == null)
        .forEach(
            inc ->
                processNames.put(
                    inc.getProcessDefinitionKey(),
                    processCache.getProcessNameOrBpmnProcessId(
                        inc.getProcessDefinitionKey(), FALLBACK_PROCESS_DEFINITION_NAME)));

    final Map<Long, List<OperationEntity>> operations =
        operationReader.getOperationsPerIncidentKey(processInstanceId);

    final Map<String, IncidentDataHolder> incData =
        collectFlowNodeDataForPropagatedIncidents(incidents, processInstanceId, treePath);

    // collect flow node statistics
    incidentResponse.setFlowNodes(
        incData.values().stream()
            .collect(
                Collectors.groupingBy(
                    IncidentDataHolder::getFinalFlowNodeId, Collectors.counting()))
            .entrySet()
            .stream()
            .map(entry -> new IncidentFlowNodeDto(entry.getKey(), entry.getValue().intValue()))
            .collect(Collectors.toList()));

    final List<IncidentDto> incidentsDtos =
        IncidentDto.sortDefault(
            IncidentDto.createFrom(incidents, operations, processNames, incData));
    incidentResponse.setIncidents(incidentsDtos);
    incidentResponse.setCount(incidents.size());
    return incidentResponse;
  }

  /**
   * Returns map incidentId -> IncidentDataHolder.
   *
   * @param incidents
   * @param processInstanceId
   * @param currentTreePath
   * @return
   */
  @Override
  public Map<String, IncidentDataHolder> collectFlowNodeDataForPropagatedIncidents(
      final List<IncidentEntity> incidents, String processInstanceId, String currentTreePath) {

    final Set<String> flowNodeInstanceIdsSet = new HashSet<>();
    final Map<String, IncidentDataHolder> incDatas = new HashMap<>();
    for (IncidentEntity inc : incidents) {
      final IncidentDataHolder incData = new IncidentDataHolder().setIncidentId(inc.getId());
      if (!String.valueOf(inc.getProcessInstanceKey()).equals(processInstanceId)) {
        final String callActivityInstanceId =
            TreePath.extractFlowNodeInstanceId(inc.getTreePath(), currentTreePath);
        incData.setFinalFlowNodeInstanceId(callActivityInstanceId);
        flowNodeInstanceIdsSet.add(callActivityInstanceId);
      } else {
        incData.setFinalFlowNodeInstanceId(String.valueOf(inc.getFlowNodeInstanceKey()));
        incData.setFinalFlowNodeId(inc.getFlowNodeId());
      }
      incDatas.put(inc.getId(), incData);
    }

    if (flowNodeInstanceIdsSet.size() > 0) {
      // select flowNodeIds by flowNodeInstanceIds
      final Map<String, String> flowNodeIdsMap =
          flowNodeStore.getFlowNodeIdsForFlowNodeInstances(flowNodeInstanceIdsSet);

      // set flow node id, where not yet set
      incDatas.values().stream()
          .filter(iData -> iData.getFinalFlowNodeId() == null)
          .forEach(
              iData ->
                  iData.setFinalFlowNodeId(flowNodeIdsMap.get(iData.getFinalFlowNodeInstanceId())));
    }
    return incDatas;
  }
}
