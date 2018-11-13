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
    EVENTS.add(IncidentIntent.DELETED.name());
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

      incidentEntity.setId(IdUtil.createId(record.getKey(), record.getMetadata().getPartitionId()));
      incidentEntity.setKey(record.getKey());
      incidentEntity.setPartitionId(record.getMetadata().getPartitionId());
      incidentEntity.setErrorType(recordValue.getErrorType());
      incidentEntity.setErrorMessage(recordValue.getErrorMessage());
      incidentEntity.setActivityId(recordValue.getElementId());
      if (recordValue.getElementInstanceKey() != 0) {
        incidentEntity.setActivityInstanceId(IdUtil.createId(recordValue.getElementInstanceKey(), record.getMetadata().getPartitionId()));
      }
      if (recordValue.getJobKey() != 0) {
        incidentEntity.setJobId(String.valueOf(recordValue.getJobKey()));
      }
      if (recordValue.getWorkflowInstanceKey() != 0) {
        incidentEntity.setWorkflowInstanceId(IdUtil.createId(recordValue.getWorkflowInstanceKey(), record.getMetadata().getPartitionId()));
      }

      org.camunda.operate.entities.IncidentState incidentState = fromZeebeIncidentIntent(intentStr);

      incidentEntity.setState(incidentState);

      incidentEntity.setPosition(record.getPosition());

      result.add(incidentEntity);

    }
    return result;
  }

  private OperateZeebeEntity convertEvent(Record record) {
    EventEntity eventEntity = new EventEntity();

    loadEventGeneralData(record, eventEntity);

    IncidentRecordValueImpl recordValue = (IncidentRecordValueImpl)record.getValue();

    if (recordValue.getWorkflowInstanceKey() != 0) {
      eventEntity.setWorkflowInstanceId(IdUtil.createId(recordValue.getWorkflowInstanceKey(), record.getMetadata().getPartitionId()));
    }
    eventEntity.setBpmnProcessId(recordValue.getBpmnProcessId());
    eventEntity.setActivityId(recordValue.getElementId());
    if (recordValue.getElementInstanceKey() != 0) {
      eventEntity.setActivityInstanceId(IdUtil.createId(recordValue.getElementInstanceKey(), record.getMetadata().getPartitionId()));
    }

    EventMetadataEntity eventMetadata = new EventMetadataEntity();
    eventMetadata.setIncidentErrorMessage(recordValue.getErrorMessage());
    eventMetadata.setIncidentErrorType(recordValue.getErrorType());
    if (recordValue.getJobKey() != 0) {
      eventMetadata.setJobId(String.valueOf(recordValue.getJobKey()));
    }
    eventEntity.setMetadata(eventMetadata);

    return eventEntity;
  }

}
