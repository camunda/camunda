/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.operate.zeebe.operation;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.OperationState;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.es.writer.BatchOperationWriter;
import org.camunda.operate.es.writer.PersistenceException;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.zeebe.JobEventTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.cmd.ClientCommandRejectedException;
import io.zeebe.client.impl.data.ZeebeObjectMapperImpl;
import io.zeebe.client.impl.event.JobEventImpl;

/**
 * Updates retries for all jobs, that has related incidents.
 */
@Component
public class UpdateRetriesHandler implements OperationHandler {

  private static final Logger logger = LoggerFactory.getLogger(UpdateRetriesHandler.class);

  @Autowired
  private WorkflowInstanceReader workflowInstanceReader;

  @Autowired
  private BatchOperationWriter batchOperationWriter;

  @Autowired
  private ZeebeClient zeebeClient;

  @Autowired
  private OperateProperties operateProperties;

  @Override
  public void handle(String workflowInstanceId) throws PersistenceException {
    final WorkflowInstanceEntity workflowInstance = workflowInstanceReader.getWorkflowInstanceById(workflowInstanceId);

    List<IncidentEntity> incidentsToResolve =
      workflowInstance.getIncidents().stream()
        .filter(inc -> inc.getState().equals(IncidentState.ACTIVE) && inc.getErrorType().equals(IncidentEntity.JOB_NO_RETRIES_ERROR_TYPE))
        .collect(Collectors.toList());

    if (incidentsToResolve.size() == 0) {
      //fail operation
      failOperationsOfCurrentType(workflowInstance, "No appropriate incidents found.");
    }

    for (IncidentEntity incident : incidentsToResolve) {
      JobEvent jobEvent = createJobEvent(workflowInstance, incident);
      try {
        zeebeClient.topicClient(workflowInstance.getTopicName()).jobClient().newUpdateRetriesCommand(jobEvent).retries(1).send().join();
        //mark operation as sent
        markAsSentOperationsOfCurrentType(workflowInstance);
      } catch (ClientCommandRejectedException ex) {
        logger.error("Zeebe command rejected: " + ex.getMessage(), ex);
        //fail operation
        failOperationsOfCurrentType(workflowInstance, ex.getMessage());
      }
    }

  }

  private void failOperationsOfCurrentType(WorkflowInstanceEntity workflowInstance, String errorMsg) throws PersistenceException {
    for (OperationEntity operation: workflowInstance.getOperations()) {
      if (operation.getState().equals(OperationState.LOCKED) && operation.getLockOwner().equals(operateProperties.getOperationExecutor().getWorkerId())) {
        operation.setState(OperationState.FAILED);
        operation.setLockExpirationTime(null);
        operation.setLockOwner(null);
        operation.setEndDate(OffsetDateTime.now());
        operation.setErrorMessage(errorMsg);
        batchOperationWriter.updateOperation(workflowInstance.getId(), operation);
        logger.debug("Operation {} failed with message: {} ", operation.getId(), operation.getErrorMessage());
      }
    }
  }

  private void markAsSentOperationsOfCurrentType(WorkflowInstanceEntity workflowInstance) throws PersistenceException {
    for (OperationEntity operation: workflowInstance.getOperations()) {
      if (operation.getState().equals(OperationState.LOCKED) && operation.getLockOwner().equals(operateProperties.getOperationExecutor().getWorkerId())) {
        operation.setState(OperationState.SENT);
        operation.setLockExpirationTime(null);
        operation.setLockOwner(null);
        batchOperationWriter.updateOperation(workflowInstance.getId(), operation);
        logger.debug("Operation {} was sent to Zeebe", operation.getId());
      }
    }
  }

  private JobEvent createJobEvent(WorkflowInstanceEntity workflowInstance, IncidentEntity incident) {
    JobEventImpl jobEvent = new JobEventImpl(new ZeebeObjectMapperImpl());
    if (incident.getJobId() != null) {
      jobEvent.setKey(Long.valueOf(incident.getJobId()));
    }
    jobEvent.setTopicName(workflowInstance.getTopicName());
    jobEvent.setPartitionId(workflowInstance.getPartitionId());
    jobEvent.setType(incident.getActivityId());
    Map<String, Object> headers = new HashMap<>();
    headers.put(JobEventTransformer.WORKFLOW_INSTANCE_KEY_HEADER, Long.valueOf(workflowInstance.getId()));
    headers.put(JobEventTransformer.WORKFLOW_KEY_HEADER, Long.valueOf(workflowInstance.getWorkflowId()));
    headers.put(JobEventTransformer.ACTIVITY_INSTANCE_KEY_HEADER, Long.valueOf(incident.getActivityInstanceId()));
    headers.put(JobEventTransformer.ACTIVITY_ID_HEADER, incident.getActivityId());
    jobEvent.setHeaders(headers);
    return jobEvent;
  }

  @Override
  public OperationType getType() {
    return OperationType.UPDATE_RETRIES;
  }
}
