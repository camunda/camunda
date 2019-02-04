package org.camunda.operate.zeebeimport;

import java.util.List;
import java.util.Map;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.entities.OperateZeebeEntity;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.zeebeimport.record.RecordImpl;
import org.camunda.operate.zeebeimport.transformers.AbstractRecordTransformer;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.zeebe.protocol.clientapi.ValueType;

@Component
public class ElasticsearchBulkProcessor extends Thread {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchBulkProcessor.class);

  @Autowired
  private TransportClient esClient;

  @Autowired
  private Map<Class<? extends OperateEntity>, ElasticsearchRequestCreator> esRequestCreatorsMap;

  @Autowired
  private Map<ValueType, AbstractRecordTransformer> zeebeRecordTransformersMap;

  @Autowired
  private ListViewZeebeRecordProcessor listViewZeebeRecordProcessor;

  @Autowired
  private DetailViewZeebeRecordProcessor detailViewZeebeRecordProcessor;

  public void persistZeebeRecords(List<? extends RecordImpl> zeebeRecords) throws PersistenceException {

      logger.debug("Writing [{}] Zeebe records to Elasticsearch", zeebeRecords.size());

      BulkRequestBuilder bulkRequest = esClient.prepareBulk();
      for (RecordImpl record : zeebeRecords) {
        final AbstractRecordTransformer transformer = zeebeRecordTransformersMap.get(record.getMetadata().getValueType());
        if (transformer == null) {
          logger.warn("Unable to transform record of type [{}]", record.getMetadata().getValueType());
        } else {
          final List<OperateZeebeEntity> operateEntities = transformer.convert(record);
          bulkRequest = addToBulk(bulkRequest, operateEntities);
        }

        //TODO new processing
        if (record.getMetadata().getValueType().equals(ValueType.WORKFLOW_INSTANCE)) {
          listViewZeebeRecordProcessor.processWorkflowInstanceRecord(record, bulkRequest);
          detailViewZeebeRecordProcessor.processWorkflowInstanceRecord(record, bulkRequest);
        } else if (record.getMetadata().getValueType().equals(ValueType.INCIDENT)) {
          listViewZeebeRecordProcessor.processIncidentRecord(record, bulkRequest);
          detailViewZeebeRecordProcessor.processIncidentRecord(record, bulkRequest);
        }
      }
      ElasticsearchUtil.processBulkRequest(bulkRequest, true);

  }

  public void persistOperateEntities(List<? extends OperateEntity> operateEntities) throws PersistenceException {

    BulkRequestBuilder bulkRequest = esClient.prepareBulk();
    bulkRequest = addToBulk(bulkRequest, operateEntities);
    ElasticsearchUtil.processBulkRequest(bulkRequest, true);

  }

  private BulkRequestBuilder addToBulk(BulkRequestBuilder bulkRequest, List<? extends OperateEntity> operateEntities) throws PersistenceException {
    for (OperateEntity operateEntity : operateEntities) {
      final ElasticsearchRequestCreator esRequestCreator = esRequestCreatorsMap.get(operateEntity.getClass());
      if (esRequestCreator == null) {
        logger.warn("Unable to persist entity of type [{}]", operateEntity.getClass());
      } else {
        bulkRequest = esRequestCreator.addRequestToBulkQuery(bulkRequest, operateEntity);
      }
    }
    return bulkRequest;
  }

}
