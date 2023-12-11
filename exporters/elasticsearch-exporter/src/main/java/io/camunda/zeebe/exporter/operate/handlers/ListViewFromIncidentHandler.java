package io.camunda.zeebe.exporter.operate.handlers;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import io.camunda.operate.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.zeebe.exporter.operate.ExportHandler;
import io.camunda.zeebe.exporter.operate.schema.templates.ListViewTemplate;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;

public class ListViewFromIncidentHandler
    implements ExportHandler<FlowNodeInstanceForListViewEntity, IncidentRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ListViewFromIncidentHandler.class);

  private ListViewTemplate listViewTemplate;

  public ListViewFromIncidentHandler(ListViewTemplate listViewTemplate) {
    this.listViewTemplate = listViewTemplate;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.INCIDENT;
  }

  @Override
  public Class<FlowNodeInstanceForListViewEntity> getEntityType() {
    return FlowNodeInstanceForListViewEntity.class;
  }

  @Override
  public boolean handlesRecord(Record<IncidentRecordValue> record) {
    return true;
  }

  @Override
  public String generateId(Record<IncidentRecordValue> record) {
    return ConversionUtils.toStringOrNull(record.getValue().getElementInstanceKey());
  }

  @Override
  public FlowNodeInstanceForListViewEntity createNewEntity(String id) {
    return new FlowNodeInstanceForListViewEntity().setId(id);
  }

  @Override
  public void updateEntity(Record<IncidentRecordValue> record,
      FlowNodeInstanceForListViewEntity entity) {
    final Intent intent = record.getIntent();
    final IncidentRecordValue recordValue = record.getValue();

    // update activity instance
    entity.setKey(recordValue.getElementInstanceKey());
    entity.setPartitionId(record.getPartitionId());
    entity.setActivityId(recordValue.getElementId());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    entity.setTenantId(tenantOrDefault(recordValue.getTenantId()));

    if (intent == IncidentIntent.CREATED) {
      entity.setErrorMessage(StringUtils.trimWhitespace(recordValue.getErrorMessage()));
    } else if (intent == IncidentIntent.RESOLVED) {
      entity.setErrorMessage(null);
    }

    // set parent
    final Long processInstanceKey = recordValue.getProcessInstanceKey();
    entity.getJoinRelation().setParent(processInstanceKey);



  }

  @Override
  public void flush(FlowNodeInstanceForListViewEntity entity, BatchRequest batchRequest)
      throws PersistenceException {
    LOGGER.debug("Activity instance for list view: id {}", entity.getId());
    final var updateFields = new HashMap<String, Object>();
    updateFields.put(ListViewTemplate.ERROR_MSG, entity.getErrorMessage());
    batchRequest.upsertWithRouting(listViewTemplate.getFullQualifiedName(), entity.getId(), entity,
        updateFields, entity.getProcessInstanceKey().toString());
  }

  @Override
  public String getIndexName() {
    return listViewTemplate.getFullQualifiedName();
  }
}
