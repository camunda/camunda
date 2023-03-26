/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.es.writer;

import static io.camunda.operate.entities.OperationType.ADD_VARIABLE;
import static io.camunda.operate.entities.OperationType.UPDATE_VARIABLE;
import static io.camunda.operate.util.CollectionUtil.getOrDefaultForNullValue;
import static io.camunda.operate.util.CollectionUtil.map;
import static io.camunda.operate.util.CollectionUtil.toSafeArrayOfStrings;
import static io.camunda.operate.util.ConversionUtils.toLongOrNull;
import static io.camunda.operate.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.ElasticsearchUtil.QueryType;
import io.camunda.operate.webapp.es.reader.IncidentReader;
import io.camunda.operate.webapp.es.reader.ListViewReader;
import io.camunda.operate.webapp.es.reader.OperationReader;
import io.camunda.operate.webapp.es.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.operate.webapp.security.UserService;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class BatchOperationWriter {

  private static final Logger logger = LoggerFactory.getLogger(BatchOperationWriter.class);

  @Autowired
  private ListViewReader listViewReader;

  @Autowired
  private IncidentReader incidentReader;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private OperationTemplate operationTemplate;

  @Autowired
  private OperationReader operationReader;

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private BatchOperationTemplate batchOperationTemplate;

  @Autowired
  private UserService userService;

  @Autowired
  private ProcessInstanceReader processInstanceReader;

  @Autowired(required = false)
  private PermissionsService permissionsService;

  /**
   * Finds operation, which are scheduled or locked with expired timeout, in the amount of configured batch size, and locks them.
   * @return list of locked operations
   * @throws PersistenceException
   */
  public List<OperationEntity> lockBatch() throws PersistenceException {
    final String workerId = operateProperties.getOperationExecutor().getWorkerId();
    final long lockTimeout = operateProperties.getOperationExecutor().getLockTimeout();
    final int batchSize = operateProperties.getOperationExecutor().getBatchSize();

    //select process instances, which has scheduled operations, or locked with expired lockExpirationTime
    final List<OperationEntity> operationEntities = operationReader.acquireOperations(batchSize);

    BulkRequest bulkRequest = new BulkRequest();

    //lock the operations
    for (OperationEntity operation: operationEntities) {
      //lock operation: update workerId, state, lockExpirationTime
      operation.setState(OperationState.LOCKED);
      operation.setLockOwner(workerId);
      operation.setLockExpirationTime(OffsetDateTime.now().plus(lockTimeout, ChronoUnit.MILLIS));

      //TODO decide with index refresh
      bulkRequest.add(createUpdateByIdRequest(operation, false));
    }
    //TODO decide with index refresh
    ElasticsearchUtil.processBulkRequest(esClient, bulkRequest, true);
    logger.debug("{} operations locked", operationEntities.size());
    return operationEntities;
  }

  private UpdateRequest createUpdateByIdRequest(OperationEntity operation, boolean refreshImmediately) throws PersistenceException {
    try {
      Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(operation), HashMap.class);

      UpdateRequest updateRequest = new UpdateRequest()
          .index(operationTemplate.getFullQualifiedName())
          .id(operation.getId())
          .doc(jsonMap)
          .retryOnConflict(UPDATE_RETRY_COUNT);
      if (refreshImmediately) {
        updateRequest = updateRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
      }
      return updateRequest;
    } catch (IOException e) {
      throw new PersistenceException(String.format("Error preparing the query to update operation [%s] for process instance id [%s]",
          operation.getId(), operation.getProcessInstanceKey()), e);
    }
  }

  public void updateOperation(OperationEntity operation) throws PersistenceException {
    final UpdateRequest updateRequest = createUpdateByIdRequest(operation, true);
    ElasticsearchUtil.executeUpdate(esClient, updateRequest);
  }

  /**
   * Schedule operations based of process instance query.
   * @param batchOperationRequest
   * @return
   */
  public BatchOperationEntity scheduleBatchOperation(CreateBatchOperationRequestDto batchOperationRequest) {
    logger.debug("Creating batch operation: operationRequest [{}]", batchOperationRequest);
    try {
      //create batch operation with unique id
      final BatchOperationEntity batchOperation = createBatchOperationEntity(batchOperationRequest.getOperationType(), batchOperationRequest.getName());

      persistBatchOperationEntity(batchOperation);

      //create single operations
      final int batchSize = operateProperties.getElasticsearch().getBatchSize();
      ConstantScoreQueryBuilder query = listViewReader.createProcessInstancesQuery(batchOperationRequest.getQuery());
      if(permissionsService != null) {
        IdentityPermission permission = batchOperationRequest.getOperationType().equals(OperationType.DELETE_PROCESS_INSTANCE) ?
            IdentityPermission.DELETE_PROCESS_INSTANCE : IdentityPermission.UPDATE_PROCESS_INSTANCE;
        QueryBuilder permissionQuery = permissionsService.createQueryForProcessesByPermission(permission);
        query = constantScoreQuery(joinWithAnd(query, permissionQuery));
      }
      QueryType queryType = QueryType.ONLY_RUNTIME;
      if (batchOperationRequest.getOperationType().equals(OperationType.DELETE_PROCESS_INSTANCE)) {
        queryType = QueryType.ALL;
      }
      String[] includeFields = new String[] {OperationTemplate.PROCESS_INSTANCE_KEY, OperationTemplate.PROCESS_DEFINITION_KEY, OperationTemplate.BPMN_PROCESS_ID};
      final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(listViewTemplate, queryType)
            .source(new SearchSourceBuilder().query(query).size(batchSize).fetchSource(includeFields, null));

      AtomicInteger operationsCount = new AtomicInteger();
      ElasticsearchUtil.scrollWith(searchRequest, esClient,
          searchHits -> {
            try {
              final List<ProcessInstanceSource> processInstanceSources = new ArrayList<>();
              for(SearchHit hit : searchHits.getHits()) {
                processInstanceSources.add(ProcessInstanceSource.fromSourceMap(hit.getSourceAsMap()));
              }
              operationsCount.addAndGet(persistOperations(processInstanceSources, batchOperation.getId(), batchOperationRequest.getOperationType(), null));
            } catch (PersistenceException e) {
              throw new RuntimeException(e);
            }
          },
          null,
          searchHits -> {
            validateTotalHits(searchHits);
            batchOperation.setInstancesCount((int)searchHits.getTotalHits().value);
          });

      //update counts
      batchOperation.setOperationsTotalCount(operationsCount.get());

      if (operationsCount.get() == 0) {
        batchOperation.setEndDate(OffsetDateTime.now());
      }

      persistBatchOperationEntity(batchOperation);

      return batchOperation;
    } catch (InvalidRequestException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new OperateRuntimeException(String.format("Exception occurred, while scheduling operation: %s", ex.getMessage()), ex);
    }
  }

  /**
   * Schedule operation for single process instance.
   * @param processInstanceKey
   * @param operationRequest
   * @return
   */
  public BatchOperationEntity scheduleSingleOperation(long processInstanceKey, CreateOperationRequestDto operationRequest) {
    logger.debug("Creating operation: processInstanceKey [{}], operation type [{}]", processInstanceKey, operationRequest.getOperationType());
    try {
      //create batch operation with unique id
      final BatchOperationEntity batchOperation = createBatchOperationEntity(operationRequest.getOperationType(), operationRequest.getName());

      //create single operations
      BulkRequest bulkRequest = new BulkRequest();
      int operationsCount = 0;

      String noOperationsReason = null;

      final OperationType operationType = operationRequest.getOperationType();
      if (operationType.equals(OperationType.RESOLVE_INCIDENT) && operationRequest.getIncidentId() == null) {
        final List<IncidentEntity> allIncidents = incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey);
        if (allIncidents.size() == 0) {
          //nothing to schedule
          //TODO delete batch operation entity
          batchOperation.setEndDate(OffsetDateTime.now());
          noOperationsReason = "No incidents found.";
        } else {
          for (IncidentEntity incident: allIncidents) {
            bulkRequest.add(getIndexOperationRequest(processInstanceKey, incident.getKey(), batchOperation.getId(), operationType));
            operationsCount++;
          }
        }
      } else if (Set.of(UPDATE_VARIABLE, ADD_VARIABLE).contains(operationType)) {
        bulkRequest.add(
            getIndexVariableOperationRequest(
                processInstanceKey,
                toLongOrNull(operationRequest.getVariableScopeId()),
                operationType,
                operationRequest.getVariableName(),
                operationRequest.getVariableValue(),
                batchOperation.getId()));
        operationsCount++;
      } else {
        bulkRequest.add(getIndexOperationRequest(processInstanceKey, toLongOrNull(operationRequest.getIncidentId()), batchOperation.getId(), operationType));
        operationsCount++;
      }
      //update process instance
      bulkRequest.add(getUpdateProcessInstanceRequest(processInstanceKey, getListViewIndicesForProcessInstances(List.of(processInstanceKey)), batchOperation.getId()));
      //update instances_count and operations_count of batch operation
      batchOperation.setOperationsTotalCount(operationsCount);
      batchOperation.setInstancesCount(1);
      //persist batch operation
      bulkRequest.add(getIndexBatchOperationRequest(batchOperation));

      ElasticsearchUtil.processBulkRequest(esClient, bulkRequest);

      return batchOperation;
    } catch (Exception ex) {
      throw new OperateRuntimeException(String.format("Exception occurred, while scheduling operation: %s", ex.getMessage()), ex);
    }
  }

  public BatchOperationEntity scheduleModifyProcessInstance(ModifyProcessInstanceRequestDto modifyRequest) {
    logger.debug("Creating modify process instance operation: processInstanceKey [{}]", modifyRequest.getProcessInstanceKey());
    try {
      final int operationsCount = modifyRequest.getModifications().size();
      final Long processInstanceKey = Long.parseLong(modifyRequest.getProcessInstanceKey());
      final BatchOperationEntity batchOperation = createBatchOperationEntity(OperationType.MODIFY_PROCESS_INSTANCE, null)
          .setOperationsTotalCount(operationsCount)
          .setInstancesCount(1);

      final OperationEntity operationEntity = createOperationEntity(
          processInstanceKey, OperationType.MODIFY_PROCESS_INSTANCE, batchOperation.getId())
          .setModifyInstructions(objectMapper.writeValueAsString(modifyRequest));

      final BulkRequest bulkRequest = new BulkRequest()
          .add(createIndexRequest(operationEntity, processInstanceKey))
          .add(getUpdateProcessInstanceRequest(processInstanceKey,
              getListViewIndicesForProcessInstances(
                  List.of(processInstanceKey)), batchOperation.getId()))
          .add(getIndexBatchOperationRequest(batchOperation));

      ElasticsearchUtil.processBulkRequest(esClient, bulkRequest);
      return batchOperation;
    } catch (Exception ex) {
      throw new OperateRuntimeException(String.format("Exception occurred, while scheduling 'modify process instance' operation: %s", ex.getMessage()), ex);
    }
  }

  private Script getUpdateBatchOperationIdScript(final String batchOperationId) {
    final Map<String,Object> paramsMap = Map.of("batchOperationId", batchOperationId);
    final String script = "if (ctx._source.batchOperationIds == null){"
        + "ctx._source.batchOperationIds = new String[]{params.batchOperationId};"
        + "} else {"
        + "ctx._source.batchOperationIds.add(params.batchOperationId);"
        + "}";
    return new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, script, paramsMap);
  }

  private BatchOperationEntity createBatchOperationEntity(OperationType operationType, String name) {
    BatchOperationEntity batchOperationEntity = new BatchOperationEntity();
    batchOperationEntity.generateId();
    batchOperationEntity.setType(operationType);
    batchOperationEntity.setName(name);
    batchOperationEntity.setStartDate(OffsetDateTime.now());
    batchOperationEntity.setUsername(userService.getCurrentUser().getUsername());
    return batchOperationEntity;
  }

  private String persistBatchOperationEntity(BatchOperationEntity batchOperationEntity) throws PersistenceException {
    try {
      IndexRequest indexRequest = getIndexBatchOperationRequest(batchOperationEntity);
      esClient.index(indexRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      logger.error("Error persisting batch operation", e);
      throw new PersistenceException(
          String.format("Error persisting batch operation of type [%s]", batchOperationEntity.getType()), e);
    }
    return batchOperationEntity.getId();
  }

  private IndexRequest getIndexBatchOperationRequest(BatchOperationEntity batchOperationEntity) throws JsonProcessingException {
    return new IndexRequest(batchOperationTemplate.getFullQualifiedName()).id(batchOperationEntity.getId()).
            source(objectMapper.writeValueAsString(batchOperationEntity), XContentType.JSON);
  }

  private int persistOperations(List<ProcessInstanceSource> processInstanceSources, String batchOperationId, OperationType operationType, String incidentId) throws PersistenceException {
    BulkRequest bulkRequest = new BulkRequest();
    int operationsCount = 0;

    List<Long> processInstanceKeys = processInstanceSources.stream().map(ProcessInstanceSource::getProcessInstanceKey).collect(Collectors.toList());
    Map<Long, List<Long>> incidentKeys = new HashMap<>();
    //prepare map of incident ids per process instance id
    if (operationType.equals(OperationType.RESOLVE_INCIDENT) && incidentId == null) {
      incidentKeys = incidentReader.getIncidentKeysPerProcessInstance(processInstanceKeys);
    }
    Map<Long,String> processInstanceIdToIndexName = null;
    try {
      processInstanceIdToIndexName = getListViewIndicesForProcessInstances(processInstanceKeys);
    } catch (IOException e) {
      throw new NotFoundException("Couldn't find index names for process instances.", e);
    }
    for (ProcessInstanceSource processInstanceSource : processInstanceSources) {
      //create single operations
      Long processInstanceKey = processInstanceSource.getProcessInstanceKey();
      if (operationType.equals(OperationType.RESOLVE_INCIDENT) && incidentId == null) {
        final List<Long> allIncidentKeys = incidentKeys.get(processInstanceKey);
        if (allIncidentKeys != null && allIncidentKeys.size() != 0) {
          for (Long incidentKey: allIncidentKeys) {
            bulkRequest.add(getIndexOperationRequest(processInstanceSource, incidentKey, batchOperationId, operationType));
            operationsCount++;
          }
        }
      } else {
        bulkRequest.add(getIndexOperationRequest(processInstanceSource, toLongOrNull(incidentId), batchOperationId, operationType));
        operationsCount++;
      }
      //update process instance
      bulkRequest.add(getUpdateProcessInstanceRequest(processInstanceKey, processInstanceIdToIndexName, batchOperationId));
    }
    ElasticsearchUtil.processBulkRequest(esClient, bulkRequest);
    return operationsCount;
  }

  private Map<Long,String> getListViewIndicesForProcessInstances(List<Long> processInstanceIds)
      throws IOException {
    final List<String> processInstanceIdsAsStrings = map(processInstanceIds, Object::toString);

    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(listViewTemplate, QueryType.ALL);
    searchRequest.source().query(QueryBuilders.idsQuery().addIds(toSafeArrayOfStrings(processInstanceIdsAsStrings)));

    final Map<Long,String> processInstanceId2IndexName = new HashMap<>();
    ElasticsearchUtil.scrollWith(searchRequest, esClient, searchHits -> {
      for(SearchHit searchHit: searchHits.getHits()){
        final String indexName = searchHit.getIndex();
        final Long id = Long.valueOf(searchHit.getId());
        processInstanceId2IndexName.put(id, indexName);
      }
    });

    if(processInstanceId2IndexName.isEmpty()){
      throw new NotFoundException(String.format("Process instances %s doesn't exists.", processInstanceIds));
    }
    return processInstanceId2IndexName;
  }

  private IndexRequest getIndexVariableOperationRequest(
      Long processInstanceKey,
      Long scopeKey,
      OperationType operationType,
      String name,
      String value,
      String batchOperationId)
      throws PersistenceException {
    OperationEntity operationEntity = createOperationEntity(processInstanceKey, operationType, batchOperationId);

    operationEntity.setScopeKey(scopeKey);
    operationEntity.setVariableName(name);
    operationEntity.setVariableValue(value);

    return createIndexRequest(operationEntity, processInstanceKey);
  }

  protected IndexRequest getIndexOperationRequest(Long processInstanceKey, Long incidentKey, String batchOperationId, OperationType operationType) throws PersistenceException {
    OperationEntity operationEntity = createOperationEntity(processInstanceKey, operationType, batchOperationId);
    operationEntity.setIncidentKey(incidentKey);

    return createIndexRequest(operationEntity, processInstanceKey);
  }

  protected IndexRequest getIndexOperationRequest(ProcessInstanceSource processInstanceSource, Long incidentKey, String batchOperationId, OperationType operationType) throws PersistenceException {
    OperationEntity operationEntity = createOperationEntity(processInstanceSource, operationType, batchOperationId);
    operationEntity.setIncidentKey(incidentKey);

    return createIndexRequest(operationEntity, processInstanceSource.getProcessInstanceKey());
  }

  private UpdateRequest getUpdateProcessInstanceRequest(Long processInstanceKey,
      final Map<Long, String> processInstanceIdToIndexName, String batchOperationId) {
    final String processInstanceId = String.valueOf(processInstanceKey);

    final String indexForProcessInstance = getOrDefaultForNullValue(processInstanceIdToIndexName,
        processInstanceKey, listViewTemplate.getFullQualifiedName());

    return new UpdateRequest().index(indexForProcessInstance).id(processInstanceId)
        .script(getUpdateBatchOperationIdScript(batchOperationId))
        .retryOnConflict(UPDATE_RETRY_COUNT);
  }

  private OperationEntity createOperationEntity(Long processInstanceKey, OperationType operationType, String batchOperationId) {

    ProcessInstanceSource processInstanceSource = new ProcessInstanceSource().setProcessInstanceKey(processInstanceKey);
    Optional<ProcessInstanceForListViewEntity> optionalProcessInstance = tryGetProcessInstance(processInstanceKey);
    optionalProcessInstance.ifPresent(processInstance -> processInstanceSource
        .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
        .setBpmnProcessId(processInstance.getBpmnProcessId()));

    return createOperationEntity(processInstanceSource, operationType, batchOperationId);
  }

  private OperationEntity createOperationEntity(ProcessInstanceSource processInstanceSource, OperationType operationType, String batchOperationId) {

    OperationEntity operationEntity = new OperationEntity();
    operationEntity.generateId();
    operationEntity.setProcessInstanceKey(processInstanceSource.getProcessInstanceKey());
    operationEntity.setProcessDefinitionKey(processInstanceSource.getProcessDefinitionKey());
    operationEntity.setBpmnProcessId(processInstanceSource.getBpmnProcessId());
    operationEntity.setType(operationType);
    operationEntity.setState(OperationState.SCHEDULED);
    operationEntity.setBatchOperationId(batchOperationId);
    operationEntity.setUsername(userService.getCurrentUser().getUsername());

    return operationEntity;
  }

  private IndexRequest createIndexRequest(OperationEntity operationEntity, Long processInstanceKey) throws PersistenceException {
    try {
      return new IndexRequest(operationTemplate.getFullQualifiedName()).id(operationEntity.getId())
          .source(objectMapper.writeValueAsString(operationEntity), XContentType.JSON);
    } catch (IOException e) {
      throw new PersistenceException(
          String.format("Error preparing the query to insert operation [%s] for process instance id [%s]", operationEntity.getType(), processInstanceKey), e);
    }
  }

  private void validateTotalHits(SearchHits hits) {
    final long totalHits = hits.getTotalHits().value;
    if (operateProperties.getBatchOperationMaxSize() != null &&
        totalHits > operateProperties.getBatchOperationMaxSize()) {
      throw new InvalidRequestException(String
          .format("Too many process instances are selected for batch operation. Maximum possible amount: %s", operateProperties.getBatchOperationMaxSize()));
    }
  }

  private Optional<ProcessInstanceForListViewEntity> tryGetProcessInstance(Long processInstanceKey) {
    ProcessInstanceForListViewEntity processInstance = null;
    try {
      processInstance = processInstanceReader.getProcessInstanceByKey(processInstanceKey);
    } catch (OperateRuntimeException ex) {
      logger.error(String.format("Failed to get process instance for key %s: %s", processInstanceKey, ex.getMessage()));
    }
    return Optional.ofNullable(processInstance);
  }

  public static class ProcessInstanceSource {

    private Long processInstanceKey;
    private Long processDefinitionKey;
    private String bpmnProcessId;

    public Long getProcessInstanceKey() {
      return processInstanceKey;
    }

    public ProcessInstanceSource setProcessInstanceKey(Long processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public Long getProcessDefinitionKey() {
      return processDefinitionKey;
    }

    public ProcessInstanceSource setProcessDefinitionKey(Long processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public String getBpmnProcessId() {
      return bpmnProcessId;
    }

    public ProcessInstanceSource setBpmnProcessId(String bpmnProcessId) {
      this.bpmnProcessId = bpmnProcessId;
      return this;
    }

    public static ProcessInstanceSource fromSourceMap(Map<String, Object> sourceMap) {
      ProcessInstanceSource processInstanceSource = new ProcessInstanceSource();
      processInstanceSource.processInstanceKey = (Long) sourceMap.get(OperationTemplate.PROCESS_INSTANCE_KEY);
      processInstanceSource.processDefinitionKey = (Long) sourceMap.get(OperationTemplate.PROCESS_DEFINITION_KEY);
      processInstanceSource.bpmnProcessId = (String) sourceMap.get(OperationTemplate.BPMN_PROCESS_ID);
      return processInstanceSource;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;
      ProcessInstanceSource that = (ProcessInstanceSource) o;
      return Objects.equals(processInstanceKey, that.processInstanceKey) &&
          Objects.equals(processDefinitionKey, that.processDefinitionKey) &&
          Objects.equals(bpmnProcessId, that.bpmnProcessId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(processInstanceKey, processDefinitionKey, bpmnProcessId);
    }
  }
}
