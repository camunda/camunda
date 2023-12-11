package io.camunda.zeebe.exporter.operate.handlers;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.camunda.operate.entities.listview.VariableForListViewEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.zeebe.exporter.operate.ExportHandler;
import io.camunda.zeebe.exporter.operate.schema.templates.ListViewTemplate;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;

// TODO: VariableForListViewEntity is not properly parameterized, so this breaks without a change in
// the operate dependency
public class ListViewFromVariableHandler
    implements ExportHandler<VariableForListViewEntity, VariableRecordValue> {

  private static final Logger logger = LoggerFactory.getLogger(ListViewFromVariableHandler.class);

  private ListViewTemplate listViewTemplate;

  public ListViewFromVariableHandler(ListViewTemplate listViewTemplate) {
    this.listViewTemplate = listViewTemplate;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.VARIABLE;
  }

  @Override
  public Class<VariableForListViewEntity> getEntityType() {
    return VariableForListViewEntity.class;
  }

  @Override
  public boolean handlesRecord(Record<VariableRecordValue> record) {
    return true;
  }

  @Override
  public String generateId(Record<VariableRecordValue> record) {
    VariableRecordValue recordValue = record.getValue();
    return VariableForListViewEntity.getIdBy(recordValue.getScopeKey(), recordValue.getName());
  }

  @Override
  public VariableForListViewEntity createNewEntity(String id) {
    return new VariableForListViewEntity().setId(id);
  }

  @Override
  public void updateEntity(Record<VariableRecordValue> record, VariableForListViewEntity entity) {

    final var recordValue = record.getValue();
    entity
        .setId(VariableForListViewEntity.getIdBy(recordValue.getScopeKey(), recordValue.getName()));
    entity.setKey(record.getKey());
    entity.setPartitionId(record.getPartitionId());
    entity.setScopeKey(recordValue.getScopeKey());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    entity.setVarName(recordValue.getName());
    entity.setVarValue(recordValue.getValue());
    entity.setTenantId(tenantOrDefault(recordValue.getTenantId()));

    // set parent
    Long processInstanceKey = recordValue.getProcessInstanceKey();
    entity.getJoinRelation().setParent(processInstanceKey);

  }

  @Override
  public void flush(VariableForListViewEntity variableEntity, BatchRequest batchRequest)
      throws PersistenceException {
    // TODO: restore insert or upsert behavior
    // final var initialIntent = cachedVariable.getLeft();

    logger.debug("Variable for list view: id {}", variableEntity.getId());
    // if (initialIntent == VariableIntent.CREATED) {
    // batchRequest.addWithRouting(listViewTemplate.getFullQualifiedName(), variableEntity,
    // variableEntity.getProcessInstanceKey().toString());
    // } else {
    final var processInstanceKey = variableEntity.getProcessInstanceKey();

    Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(ListViewTemplate.VAR_NAME, variableEntity.getVarName());
    updateFields.put(ListViewTemplate.VAR_VALUE, variableEntity.getVarValue());
    batchRequest.upsertWithRouting(listViewTemplate.getFullQualifiedName(), variableEntity.getId(),
        variableEntity, updateFields, processInstanceKey.toString());
    // }

  }

}
