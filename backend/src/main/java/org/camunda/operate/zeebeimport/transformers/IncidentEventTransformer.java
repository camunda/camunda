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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.entities.EventMetadataEntity;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.OperateZeebeEntity;
import org.camunda.operate.util.IdUtil;
import org.camunda.operate.zeebeimport.record.value.IncidentRecordValueImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import io.zeebe.exporter.record.Record;
import io.zeebe.protocol.intent.IncidentIntent;
import static org.camunda.operate.entities.IncidentState.fromZeebeIncidentIntent;

@Component
public class IncidentEventTransformer implements AbstractRecordTransformer {

  private static final Logger logger = LoggerFactory.getLogger(IncidentEventTransformer.class);

  private final static Set<String> EVENTS = new HashSet<>();

  static {
    EVENTS.add(IncidentIntent.CREATED.name());
    EVENTS.add(IncidentIntent.RESOLVED.name());
  }

  @Override
  public List<OperateZeebeEntity> convert(Record record) {

//    ZeebeUtil.ALL_EVENTS_LOGGER.debug(event.toJson());

    List<OperateZeebeEntity> result = new ArrayList<>();

    result.add(convertEvent(record));

    final String intentStr = record.getMetadata().getIntent().name();

    if (EVENTS.contains(intentStr)) {

//      logger.debug(event.toJson());

      IncidentEntity incidentEntity = new IncidentEntity();

      IncidentRecordValueImpl recordValue = (IncidentRecordValueImpl)record.getValue();

      incidentEntity.setId(IdUtil.getId(record));
      incidentEntity.setKey(record.getKey());
      incidentEntity.setPartitionId(record.getMetadata().getPartitionId());
      incidentEntity.setErrorType(recordValue.getErrorType());
      incidentEntity.setErrorMessage(recordValue.getErrorMessage());
      incidentEntity.setActivityId(recordValue.getElementId());
      if (recordValue.getElementInstanceKey() != 0) {
        incidentEntity.setActivityInstanceId(IdUtil.getId(recordValue.getElementInstanceKey(), record));
      }
      if (recordValue.getJobKey() != 0) {
        incidentEntity.setJobId(recordValue.getJobKey());
      }
      if (recordValue.getWorkflowInstanceKey() != 0) {
        incidentEntity.setWorkflowInstanceId(IdUtil.getId(recordValue.getWorkflowInstanceKey(), record));
      }

      org.camunda.operate.entities.IncidentState incidentState = fromZeebeIncidentIntent(intentStr);

      incidentEntity.setState(incidentState);

      result.add(incidentEntity);

    }
    return result;
  }

  private OperateZeebeEntity convertEvent(Record record) {
    EventEntity eventEntity = new EventEntity();

    loadEventGeneralData(record, eventEntity);

    IncidentRecordValueImpl recordValue = (IncidentRecordValueImpl)record.getValue();

    if (recordValue.getWorkflowInstanceKey() != 0) {
      eventEntity.setWorkflowInstanceId(IdUtil.getId(recordValue.getWorkflowInstanceKey(), record));
    }
    eventEntity.setBpmnProcessId(recordValue.getBpmnProcessId());
    eventEntity.setActivityId(recordValue.getElementId());
    if (recordValue.getElementInstanceKey() != 0) {
      eventEntity.setActivityInstanceId(IdUtil.getId(recordValue.getElementInstanceKey(), record));
    }

    EventMetadataEntity eventMetadata = new EventMetadataEntity();
    eventMetadata.setIncidentErrorMessage(recordValue.getErrorMessage());
    eventMetadata.setIncidentErrorType(recordValue.getErrorType());
    if (recordValue.getJobKey() != 0) {
      eventMetadata.setJobId(IdUtil.getId(recordValue.getJobKey(), record));
    }
    eventEntity.setMetadata(eventMetadata);

    return eventEntity;
  }

}
