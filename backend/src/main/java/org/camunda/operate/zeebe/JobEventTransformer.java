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
package org.camunda.operate.zeebe;

import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.es.writer.EntityStorage;
import org.camunda.operate.util.ZeebeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.api.subscription.JobEventHandler;

@Component
public class JobEventTransformer extends AbstractEventTransformer implements JobEventHandler {

  public static final String WORKFLOW_KEY_HEADER = "workflowKey";
  public static final String WORKFLOW_INSTANCE_KEY_HEADER = "workflowInstanceKey";
  public static final String BPMN_PROCESS_ID_HEADER = "bpmnProcessId";
  public static final String ACTIVITY_ID_HEADER = "activityId";
  public static final String ACTIVITY_INSTANCE_KEY_HEADER = "activityInstanceKey";

  @Autowired private EntityStorage entityStorage;

  @Override
  public void onJobEvent(JobEvent event) throws Exception {

    ZeebeUtil.ALL_EVENTS_LOGGER.debug(event.toJson());
    convertEvent(event);

  }

  private void convertEvent(JobEvent event) throws InterruptedException {
    //we will store sequence flows separately, no need to store them in events
    if (!event.getState().equals(io.zeebe.client.api.events.WorkflowInstanceState.SEQUENCE_FLOW_TAKEN)) {
      EventEntity eventEntity = new EventEntity();
      loadEventGeneralData(event, eventEntity);
      eventEntity.setJobType(event.getType());
      eventEntity.setPayload(event.getPayload());
      if (event.getWorker() != null) {
        eventEntity.setJobWorker(event.getWorker());
      }
      eventEntity.setJobRetries(event.getRetries());
      //check headers to get context info
      final Object workflowKey = event.getHeaders().get(WORKFLOW_KEY_HEADER);
      if (workflowKey != null) {
        eventEntity.setWorkflowId(String.valueOf(workflowKey));
      }

      final Object workflowInstanceKey = event.getHeaders().get(WORKFLOW_INSTANCE_KEY_HEADER);
      if (workflowInstanceKey != null) {
        eventEntity.setWorkflowInstanceId(String.valueOf(workflowInstanceKey));
      }
      final Object bpmnProcessId = event.getHeaders().get(BPMN_PROCESS_ID_HEADER);
      if (bpmnProcessId != null) {
        eventEntity.setBpmnProcessId((String) bpmnProcessId);
      }
      final Object activityId = event.getHeaders().get(ACTIVITY_ID_HEADER);
      if (activityId != null) {
        eventEntity.setActivityId((String) activityId);
      }
      final Object activityInstanceKey = event.getHeaders().get(ACTIVITY_INSTANCE_KEY_HEADER);
      if (activityInstanceKey != null) {
        eventEntity.setActivityInstanceId(String.valueOf(activityInstanceKey));
      }

      //TODO will wait till capacity available, can throw InterruptedException
      entityStorage.getOperateEntititesQueue(event.getMetadata().getTopicName()).put(eventEntity);
    }
  }
}
