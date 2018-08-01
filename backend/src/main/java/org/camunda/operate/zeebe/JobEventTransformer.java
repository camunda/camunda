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

import java.time.Instant;
import java.util.Map;

import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.entities.EventMetadataEntity;
import org.camunda.operate.es.writer.EntityStorage;
import org.camunda.operate.util.DateUtil;
import org.camunda.operate.util.ZeebeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.api.record.RecordMetadata;
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
    EventEntity eventEntity = new EventEntity();

    loadEventGeneralData(event, eventEntity);

    //check headers to get context info
    Map<String, Object> headers = event.getHeaders();

    final Object workflowKey = headers.get(WORKFLOW_KEY_HEADER);
    if (workflowKey != null) {
      eventEntity.setWorkflowId(String.valueOf(workflowKey));
    }

    final Object workflowInstanceKey = headers.get(WORKFLOW_INSTANCE_KEY_HEADER);
    if (workflowInstanceKey != null) {
      eventEntity.setWorkflowInstanceId(String.valueOf(workflowInstanceKey));
    }

    final Object bpmnProcessId = headers.get(BPMN_PROCESS_ID_HEADER);
    if (bpmnProcessId != null) {
      eventEntity.setBpmnProcessId((String) bpmnProcessId);
    }

    final Object activityId = headers.get(ACTIVITY_ID_HEADER);
    if (activityId != null) {
      eventEntity.setActivityId((String) activityId);
    }

    final Object activityInstanceKey = headers.get(ACTIVITY_INSTANCE_KEY_HEADER);
    if (activityInstanceKey != null) {
      eventEntity.setActivityInstanceId(String.valueOf(activityInstanceKey));
    }

    eventEntity.setPayload(event.getPayload());

    EventMetadataEntity eventMetadata = new EventMetadataEntity();
    eventMetadata.setJobType(event.getType());
    eventMetadata.setJobRetries(event.getRetries());
    eventMetadata.setJobWorker(event.getWorker());
    eventMetadata.setJobCustomHeaders(event.getCustomHeaders());

    eventMetadata.setJobId(String.valueOf(event.getKey()));

    Instant jobDeadline = event.getDeadline();
    if (jobDeadline != null) {
      eventMetadata.setJobDeadline(DateUtil.toOffsetDateTime(jobDeadline));
    }

    eventEntity.setMetadata(eventMetadata);

    RecordMetadata metadata = event.getMetadata();
    String topicName = metadata.getTopicName();

    // TODO will wait till capacity available, can throw InterruptedException
    entityStorage.getOperateEntitiesQueue(topicName).put(eventEntity);
  }

}
