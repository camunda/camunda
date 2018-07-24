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
package org.camunda.operate.es.writer;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.OperationState;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.es.types.WorkflowInstanceType;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.rest.dto.WorkflowInstanceBatchOperationDto;
import org.camunda.operate.rest.dto.WorkflowInstanceRequestDto;
import org.camunda.operate.rest.exception.InvalidRequestException;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class BatchOperationWriter {

  private static Logger logger = LoggerFactory.getLogger(ElasticsearchUtil.class);

  @Autowired
  private WorkflowInstanceReader workflowInstanceReader;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private TransportClient esClient;

  @Autowired
  @Qualifier("esObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired
  private WorkflowInstanceType workflowInstanceType;

  public void scheduleBatchOperation(WorkflowInstanceBatchOperationDto batchOperationRequest) throws PersistenceException {

    final int batchSize = operateProperties.getElasticsearch().getInsertBatchSize();

    final SearchRequestBuilder searchRequest = workflowInstanceReader.createSearchRequest(new WorkflowInstanceRequestDto(batchOperationRequest.getQueries()));
    TimeValue keepAlive = new TimeValue(60000);

    SearchResponse scrollResp = searchRequest.setScroll(keepAlive).setSize(batchSize).get();

    SearchHits hits = scrollResp.getHits();
    validateTotalHits(hits);

    do {
      persistOperations(hits, batchOperationRequest);

      scrollResp = esClient.prepareSearchScroll(scrollResp.getScrollId()).setScroll(keepAlive).get();
      hits = scrollResp.getHits();

    } while (hits.getHits().length != 0);

  }

  private void persistOperations(SearchHits hits, WorkflowInstanceBatchOperationDto batchOperationRequest) throws PersistenceException {
    //create bulk query to persist operations for workflow instances
    logger.debug("Persisting [{}] operations to Elasticsearch", hits.getHits().length);
    BulkRequestBuilder bulkRequest = esClient.prepareBulk();
    for (SearchHit searchHit : hits.getHits()) {
      final WorkflowInstanceEntity workflowInstanceEntity = ElasticsearchUtil.fromSearchHit(searchHit.getSourceAsString(), objectMapper);
      bulkRequest.add(createUpdateRequest(workflowInstanceEntity, batchOperationRequest));
    }
    ElasticsearchUtil.processBulkRequest(bulkRequest);
  }

  private UpdateRequestBuilder createUpdateRequest(WorkflowInstanceEntity entity, WorkflowInstanceBatchOperationDto batchOperationRequest)
    throws PersistenceException {
    OperationEntity operationEntity = new OperationEntity();
    operationEntity.setType(batchOperationRequest.getOperationType());
    operationEntity.setStartDate(OffsetDateTime.now());
    operationEntity.setState(OperationState.SCHEDULED);

    try {
      Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(operationEntity), HashMap.class);
      Map<String, Object> params = new HashMap<>();
      params.put("operation", jsonMap);

      String script = "ctx._source.operations.add(params.operation);";

      Script updateScript = new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, script, params);
      return esClient
        .prepareUpdate(workflowInstanceType.getType(), workflowInstanceType.getType(), entity.getId())
        .setScript(updateScript);
    } catch (IOException e) {
      logger.error("Error preparing the query to insert operation", e);
      throw new PersistenceException(String.format("Error preparing the query to insert operation [%s] for workflow instance id [%s]",
        batchOperationRequest.getOperationType(), entity.getId()), e);
    }

  }

  private void validateTotalHits(SearchHits hits) {
    final long totalHits = hits.getTotalHits();
    if (operateProperties.getBatchOperationMaxSize() != null &&
      totalHits > operateProperties.getBatchOperationMaxSize()) {
      throw new InvalidRequestException(String
        .format("Too many workflow instances are selected for batch operation. Maximum possible amount: %s", operateProperties.getBatchOperationMaxSize()));
    }
  }

}
