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
package org.camunda.operate.zeebeimport.transformers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.entities.EventMetadataEntity;
import org.camunda.operate.entities.OperateZeebeEntity;
import org.camunda.operate.util.DateUtil;
import org.camunda.operate.util.IdUtil;
import org.camunda.operate.zeebeimport.record.value.JobRecordValueImpl;
import org.springframework.stereotype.Component;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.job.Headers;
import io.zeebe.protocol.intent.JobIntent;

@Component
public class JobEventTransformer implements AbstractRecordTransformer {

  private final static Set<String> EVENTS = new HashSet<>();

  static {
    EVENTS.add(JobIntent.CREATED.name());
    EVENTS.add(JobIntent.ACTIVATED.name());
    EVENTS.add(JobIntent.COMPLETED.name());
    EVENTS.add(JobIntent.TIMED_OUT.name());
    EVENTS.add(JobIntent.FAILED.name());
    EVENTS.add(JobIntent.RETRIES_UPDATED.name());
    EVENTS.add(JobIntent.CANCELED.name());
  }


  @Override
  public List<OperateZeebeEntity> convert(Record record) {

//    ZeebeUtil.ALL_EVENTS_LOGGER.debug(event.toJson());
    List<OperateZeebeEntity> result = new ArrayList<>();

    final String intentStr = record.getMetadata().getIntent().name();

    if (!EVENTS.contains(intentStr)) {
      return result;
    }

    EventEntity eventEntity = new EventEntity();

    loadEventGeneralData(record, eventEntity);

    JobRecordValueImpl recordValue = (JobRecordValueImpl)record.getValue();

    //check headers to get context info
    Headers headers = recordValue.getHeaders();

    final long workflowKey = headers.getWorkflowKey();
    if (workflowKey != 0) {
      eventEntity.setWorkflowId(String.valueOf(workflowKey));
    }

    final long workflowInstanceKey = headers.getWorkflowInstanceKey();
    if (workflowInstanceKey != 0) {
      eventEntity.setWorkflowInstanceId(IdUtil.getId(workflowInstanceKey, record));
    }

    eventEntity.setBpmnProcessId(headers.getBpmnProcessId());

    eventEntity.setActivityId(headers.getElementId());

    final long activityInstanceKey = headers.getElementInstanceKey();
    if (activityInstanceKey != 0) {
      eventEntity.setActivityInstanceId(IdUtil.getId(activityInstanceKey, record));
    }

    EventMetadataEntity eventMetadata = new EventMetadataEntity();
    eventMetadata.setJobType(recordValue.getType());
    eventMetadata.setJobRetries(recordValue.getRetries());
    eventMetadata.setJobWorker(recordValue.getWorker());
    eventMetadata.setJobCustomHeaders(recordValue.getCustomHeaders());

    eventMetadata.setJobId(String.valueOf(record.getKey()));

    Instant jobDeadline = recordValue.getDeadline();
    if (jobDeadline != null) {
      eventMetadata.setJobDeadline(DateUtil.toOffsetDateTime(jobDeadline));
    }

    eventEntity.setMetadata(eventMetadata);

    result.add(eventEntity);

    return result;
  }


}
