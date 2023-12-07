package io.camunda.zeebe.exporter.operate.handlers;

import io.camunda.operate.entities.dmn.DecisionInstanceEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.zeebe.exporter.operate.ExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;

public class DecisionInstanceHandler
    implements ExportHandler<DecisionInstanceEntity, DecisionEvaluationRecordValue> {

  @Override
  public ValueType handlesValueType() {
    return ValueType.DECISION_EVALUATION;
  }

  @Override
  public boolean handlesRecord(Record<DecisionEvaluationRecordValue> record) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public String generateId(Record<DecisionEvaluationRecordValue> record) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public DecisionInstanceEntity createNewEntity(String id) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void updateEntity(Record<DecisionEvaluationRecordValue> record,
      DecisionInstanceEntity entity) {
    // TODO Auto-generated method stub

  }

  @Override
  public void flush(DecisionInstanceEntity entity, BatchRequest batchRequest)
      throws PersistenceException {
    // TODO Auto-generated method stub

  }

}
