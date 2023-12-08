package io.camunda.zeebe.exporter.operate.handlers;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.zeebe.exporter.operate.ExportHandler;
import io.camunda.zeebe.exporter.operate.schema.indices.DecisionIndex;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;

public class DecisionDefinitionHandler implements ExportHandler<DecisionDefinitionEntity, DecisionRecordValue> {

  private static final Logger logger = LoggerFactory.getLogger(
      DecisionDefinitionHandler.class);
  
  private final static Set<Intent> STATES = new HashSet<>();
  static {
    STATES.add(DecisionIntent.CREATED);
  }

  private DecisionIndex decisionIndex;
  
  public DecisionDefinitionHandler(DecisionIndex decisionIndex) {
    this.decisionIndex = decisionIndex;
  }
  
  @Override
  public ValueType getHandledValueType() {
    return ValueType.DECISION;
  }
  
  @Override
  public Class<DecisionDefinitionEntity> getEntityType() {
    return DecisionDefinitionEntity.class;
  }

  @Override
  public boolean handlesRecord(Record<DecisionRecordValue> record) {
    return STATES.contains(record.getIntent());
  }

  @Override
  public String generateId(Record<DecisionRecordValue> record) {
    return String.valueOf(record.getValue().getDecisionKey());
  }

  @Override
  public DecisionDefinitionEntity createNewEntity(String id) {
    return new DecisionDefinitionEntity()
        .setId(id);
  }

  @Override
  public void updateEntity(Record<DecisionRecordValue> record, DecisionDefinitionEntity entity) {
    DecisionRecordValue decision = record.getValue();
    
    entity
      .setKey(decision.getDecisionKey())
      .setName(decision.getDecisionName())
      .setVersion(decision.getVersion())
      .setDecisionId(decision.getDecisionId())
      .setDecisionRequirementsId(decision.getDecisionRequirementsId())
      .setDecisionRequirementsKey(decision.getDecisionRequirementsKey())
      .setTenantId(tenantOrDefault(decision.getTenantId()));
    
  }

  @Override
  public void flush(DecisionDefinitionEntity entity, BatchRequest batchRequest)
      throws PersistenceException {
    logger.debug("Decision: key {}, decisionId {}", entity.getKey(), entity.getDecisionId());
    batchRequest.addWithId(decisionIndex.getFullQualifiedName(), ConversionUtils.toStringOrNull(entity.getKey()), entity);
    
  }

}
