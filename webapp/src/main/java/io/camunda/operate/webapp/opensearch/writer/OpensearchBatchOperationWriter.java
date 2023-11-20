/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.opensearch.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.ListViewStore;
import io.camunda.operate.store.OperationStore;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.store.opensearch.dsl.RequestDSL;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.opensearch.OpenSearchQueryHelper;
import io.camunda.operate.webapp.reader.IncidentReader;
import io.camunda.operate.webapp.reader.ListViewReader;
import io.camunda.operate.webapp.reader.OperationReader;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.operate.webapp.security.UserService;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static io.camunda.operate.entities.OperationType.ADD_VARIABLE;
import static io.camunda.operate.entities.OperationType.UPDATE_VARIABLE;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.QueryType.ALL;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.util.CollectionUtil.getOrDefaultForNullValue;
import static io.camunda.operate.util.ConversionUtils.toLongOrNull;
import static io.camunda.operate.util.ExceptionHelper.withOperateRuntimeException;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchBatchOperationWriter implements io.camunda.operate.webapp.writer.BatchOperationWriter {

  private static final Logger logger = LoggerFactory.getLogger(OpensearchBatchOperationWriter.class);

  @Autowired
  private ListViewReader listViewReader;

  @Autowired
  private IncidentReader incidentReader;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private RichOpenSearchClient richOpenSearchClient;

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

  @Autowired
  private OperationStore operationStore;

  @Autowired
  private ListViewStore listViewStore;

  @Autowired
  private OpenSearchQueryHelper openSearchQueryHelper;

  /**
   * Finds operation, which are scheduled or locked with expired timeout, in the amount of configured batch size, and locks them.
   * @return list of locked operations
   * @throws PersistenceException
   */
  @Override
  public List<OperationEntity> lockBatch() throws PersistenceException {
    final String workerId = operateProperties.getOperationExecutor().getWorkerId();
    final long lockTimeout = operateProperties.getOperationExecutor().getLockTimeout();
    final int batchSize = operateProperties.getOperationExecutor().getBatchSize();

    //select process instances, which has scheduled operations, or locked with expired lockExpirationTime
    final List<OperationEntity> operationEntities = operationReader.acquireOperations(batchSize);

    BatchRequest batchRequest = operationStore.newBatchRequest();

    //lock the operations
    for (OperationEntity operation: operationEntities) {
      //lock operation: update workerId, state, lockExpirationTime
      operation.setState(OperationState.LOCKED);
      operation.setLockOwner(workerId);
      operation.setLockExpirationTime(OffsetDateTime.now().plus(lockTimeout, ChronoUnit.MILLIS));

      //TODO decide with index refresh
      batchRequest.update(operationTemplate.getFullQualifiedName(), operation.getId(), operation);
    }
    //TODO decide with index refresh
    batchRequest.executeWithRefresh();
    logger.debug("{} operations locked", operationEntities.size());
    return operationEntities;
  }

  @Override
  public void updateOperation(OperationEntity operation) throws PersistenceException {
    operationStore.update(operation, true);
  }

  /**
   * Schedule operations based of process instance query.
   * @param batchOperationRequest
   * @return
   */
  @Override
  public BatchOperationEntity scheduleBatchOperation(CreateBatchOperationRequestDto batchOperationRequest) {
    logger.debug("Creating batch operation: operationRequest [{}]", batchOperationRequest);
    try {
      //add batch operation with unique id
      final BatchOperationEntity batchOperation = createBatchOperationEntity(batchOperationRequest.getOperationType(), batchOperationRequest.getName());

      var operationsCount = addOperations(batchOperationRequest, batchOperation);

      //update counts
      batchOperation.setOperationsTotalCount(operationsCount);

      if (operationsCount == 0) {
        batchOperation.setEndDate(OffsetDateTime.now());
      }
      operationStore.add(batchOperation);
      return batchOperation;
    } catch (InvalidRequestException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new OperateRuntimeException(String.format("Exception occurred, while scheduling operation: %s", ex.getMessage()), ex);
    }
  }

  private int addOperations(CreateBatchOperationRequestDto batchOperationRequest, BatchOperationEntity batchOperation) throws IOException {
    final int batchSize = operateProperties.getElasticsearch().getBatchSize();
    Query query = openSearchQueryHelper.createProcessInstancesQuery(batchOperationRequest.getQuery());
    if(permissionsService != null) {
      IdentityPermission permission = batchOperationRequest.getOperationType() == OperationType.DELETE_PROCESS_INSTANCE ?
        IdentityPermission.DELETE_PROCESS_INSTANCE : IdentityPermission.UPDATE_PROCESS_INSTANCE;
      var allowed = permissionsService.getProcessesWithPermission(permission);
      var permissionQuery = allowed.isAll() ? matchAll() : stringTerms(ListViewTemplate.BPMN_PROCESS_ID, allowed.getIds());
      query = constantScore(withTenantCheck(and(query, permissionQuery)));
    }
    final RequestDSL.QueryType queryType = batchOperationRequest.getOperationType() == OperationType.DELETE_PROCESS_INSTANCE ? ALL : ONLY_RUNTIME;
    var searchRequestBuilder = searchRequestBuilder(listViewTemplate, queryType)
      .query(query)
      .size(batchSize)
      .source(sourceInclude(OperationTemplate.PROCESS_INSTANCE_KEY, OperationTemplate.PROCESS_DEFINITION_KEY, OperationTemplate.BPMN_PROCESS_ID));

    AtomicInteger operationsCount = new AtomicInteger();

    final Consumer<List<Hit<ProcessInstanceSource>>> hitsConsumer = hits -> withOperateRuntimeException (() ->{
      final List<ProcessInstanceSource> processInstanceSources = hits.stream().map(Hit::source).toList();
      return operationsCount.addAndGet(persistOperations(processInstanceSources, batchOperation.getId(), batchOperationRequest, null));
    });

    Consumer<HitsMetadata<ProcessInstanceSource>> hitsMetadataConsumer = hitsMeta -> {
      validateTotalHits(hitsMeta);
      batchOperation.setInstancesCount((int) hitsMeta.total().value());
    };

    richOpenSearchClient.doc().unsafeScrollWith(searchRequestBuilder, hitsConsumer, hitsMetadataConsumer, ProcessInstanceSource.class, false);

    return operationsCount.get();
  }

  private void validateTotalHits(HitsMetadata<?> hitsMeta) {
    final long totalHits = hitsMeta.total().value();
    final Long maxSize = operateProperties.getBatchOperationMaxSize();
    if (maxSize != null && totalHits > operateProperties.getBatchOperationMaxSize()) {
      throw new InvalidRequestException(String.format("Too many process instances are selected for batch operation. Maximum possible amount: %s", maxSize));
    }
  }

  /**
   * Schedule operation for single process instance.
   * @param processInstanceKey
   * @param operationRequest
   * @return
   */
  @Override
  public BatchOperationEntity scheduleSingleOperation(long processInstanceKey, CreateOperationRequestDto operationRequest) {
    logger.debug("Creating operation: processInstanceKey [{}], operation type [{}]", processInstanceKey, operationRequest.getOperationType());
    try {
      //add batch operation with unique id
      final BatchOperationEntity batchOperation = createBatchOperationEntity(operationRequest.getOperationType(),
          operationRequest.getName());

      //add single operations
      var batchRequest = operationStore.newBatchRequest();
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
          for (IncidentEntity incident : allIncidents) {
            OperationEntity operationEntity = createOperationEntity(processInstanceKey, operationType, batchOperation.getId());
            operationEntity.setIncidentKey(incident.getKey());
            batchRequest.add(operationTemplate.getFullQualifiedName(), operationEntity);
            operationsCount++;
          }
        }
      } else if (Set.of(UPDATE_VARIABLE, ADD_VARIABLE).contains(operationType)) {
        OperationEntity operationEntity = createOperationEntity(processInstanceKey, operationType, batchOperation.getId()).setScopeKey(toLongOrNull(operationRequest.getVariableScopeId()))
            .setVariableName(operationRequest.getVariableName()).setVariableValue(operationRequest.getVariableValue());
        batchRequest.add(operationTemplate.getFullQualifiedName(), operationEntity);
        operationsCount++;
      } else {
        OperationEntity operationEntity = createOperationEntity(processInstanceKey, operationType, batchOperation.getId()).setIncidentKey(toLongOrNull(operationRequest.getIncidentId()));
        batchRequest.add(operationTemplate.getFullQualifiedName(), operationEntity);
        operationsCount++;
      }
      //update process instance
      final String processInstanceId = String.valueOf(processInstanceKey);
      var processInstanceIdToIndexName = listViewStore.getListViewIndicesForProcessInstances(List.of(processInstanceKey));
      final String indexForProcessInstance = getOrDefaultForNullValue(processInstanceIdToIndexName, processInstanceKey,
          listViewTemplate.getFullQualifiedName());

      String script = "if (ctx._source.batchOperationIds == null){"
          + "ctx._source.batchOperationIds = new String[]{params.batchOperationId};" + "} else {"
          + "ctx._source.batchOperationIds.add(params.batchOperationId);" + "}";
      batchRequest.updateWithScript(indexForProcessInstance, processInstanceId, script, Map.of("batchOperationId", batchOperation.getId()));

      //update instances_count and operations_count of batch operation
      batchOperation.setOperationsTotalCount(operationsCount);
      batchOperation.setInstancesCount(1);
      //persist batch operation
      batchRequest.add(batchOperationTemplate.getFullQualifiedName(), batchOperation);

      batchRequest.execute();
      return batchOperation;
    } catch (io.camunda.operate.store.NotFoundException nfe){
      throw new OperateRuntimeException(String.format("Exception occurred, while scheduling operation: %s", nfe.getMessage()), new NotFoundException(nfe.getMessage()));
    } catch (Exception ex) {
      throw new OperateRuntimeException(String.format("Exception occurred, while scheduling operation: %s", ex.getMessage()), ex);
    }
  }

  @Override
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

      var batchRequest = operationStore.newBatchRequest();

      var processInstanceIdToIndexName = listViewStore.getListViewIndicesForProcessInstances(List.of(processInstanceKey));
      var processInstanceId = String.valueOf(processInstanceKey);
      var indexForProcessInstance = getOrDefaultForNullValue(processInstanceIdToIndexName, processInstanceKey, listViewTemplate.getFullQualifiedName());
      Map<String,Object> params = Map.of("batchOperationId", batchOperation.getId());
      var script = "if (ctx._source.batchOperationIds == null){"
          + "ctx._source.batchOperationIds = new String[]{params.batchOperationId};"
          + "} else {"
          + "ctx._source.batchOperationIds.add(params.batchOperationId);"
          + "}";

      batchRequest
          .add(operationTemplate.getFullQualifiedName(), operationEntity)
          .updateWithScript(indexForProcessInstance, processInstanceId, script, params)
          .add(batchOperationTemplate.getFullQualifiedName(), batchOperation);

      batchRequest.execute();
      return batchOperation;
    } catch (Exception ex) {
      throw new OperateRuntimeException(String.format("Exception occurred, while scheduling 'modify process instance' operation: %s", ex.getMessage()), ex);
    }
  }

  @Override
  public BatchOperationEntity scheduleDeleteDecisionDefinition(DecisionDefinitionEntity decisionDefinitionEntity) {
    Long decisionDefinitionKey = decisionDefinitionEntity.getKey();
    OperationType operationType = OperationType.DELETE_DECISION_DEFINITION;

    // Create batch operation
    String batchOperationName = String.format("%s - Version %s", decisionDefinitionEntity.getName(), decisionDefinitionEntity.getVersion());
    final BatchOperationEntity batchOperation = createBatchOperationEntity(operationType, batchOperationName)
      .setOperationsTotalCount(1).setInstancesCount(0);

    // Create operation
    final OperationEntity operationEntity = new OperationEntity();
    operationEntity.generateId();
    operationEntity.setDecisionDefinitionKey(decisionDefinitionKey);
    operationEntity.setType(operationType);
    operationEntity.setState(OperationState.SCHEDULED);
    operationEntity.setBatchOperationId(batchOperation.getId());
    operationEntity.setUsername(userService.getCurrentUser().getUsername());

    // Create request
    try {
      var batchRequest = operationStore.newBatchRequest()
        .add(operationTemplate.getFullQualifiedName(), operationEntity)
        .add(batchOperationTemplate.getFullQualifiedName(), batchOperation);
      batchRequest.execute();
      return batchOperation;
    } catch (Exception ex) {
      throw new OperateRuntimeException(String.format("Exception occurred, while scheduling 'delete decision definition' operation: %s", ex.getMessage()), ex);
    }
  }

  @Override
  public BatchOperationEntity scheduleDeleteProcessDefinition(ProcessEntity processEntity) {
    Long processDefinitionKey = processEntity.getKey();
    OperationType operationType = OperationType.DELETE_PROCESS_DEFINITION;

    // Create batch operation
    String batchOperationName = String.format("%s - Version %s", processEntity.getName(), processEntity.getVersion());
    final BatchOperationEntity batchOperation = createBatchOperationEntity(operationType, batchOperationName)
      .setOperationsTotalCount(1)
      .setInstancesCount(0);

    // Create operation
    final OperationEntity operationEntity = new OperationEntity();
    operationEntity.generateId();
    operationEntity.setProcessDefinitionKey(processDefinitionKey);
    operationEntity.setType(operationType);
    operationEntity.setState(OperationState.SCHEDULED);
    operationEntity.setBatchOperationId(batchOperation.getId());
    operationEntity.setUsername(userService.getCurrentUser().getUsername());

    // Create request
    try {
      var batchRequest = operationStore.newBatchRequest()
        .add(operationTemplate.getFullQualifiedName(), operationEntity)
        .add(batchOperationTemplate.getFullQualifiedName(), batchOperation);
      batchRequest.execute();
      return batchOperation;
    } catch (Exception ex) {
      throw new OperateRuntimeException(String.format("Exception occurred, while scheduling 'delete process definition' operation: %s", ex.getMessage()), ex);
    }
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

  private int persistOperations(List<ProcessInstanceSource> processInstanceSources, String batchOperationId,
                                CreateBatchOperationRequestDto batchOperationRequest, String incidentId) throws PersistenceException {
    var batchRequest = operationStore.newBatchRequest();
    int operationsCount = 0;
    OperationType operationType = batchOperationRequest.getOperationType();

    List<Long> processInstanceKeys = processInstanceSources.stream().map(ProcessInstanceSource::getProcessInstanceKey).collect(Collectors.toList());
    Map<Long, List<Long>> incidentKeys = new HashMap<>();
    //prepare map of incident ids per process instance id
    if (operationType.equals(OperationType.RESOLVE_INCIDENT) && incidentId == null) {
      incidentKeys = incidentReader.getIncidentKeysPerProcessInstance(processInstanceKeys);
    }
    Map<Long,String> processInstanceIdToIndexName;
    try {
      processInstanceIdToIndexName = listViewStore.getListViewIndicesForProcessInstances(processInstanceKeys);
    } catch (IOException e) {
      throw new NotFoundException("Couldn't find index names for process instances.", e);
    }
    for (ProcessInstanceSource processInstanceSource : processInstanceSources) {
      //add single operations
      Long processInstanceKey = processInstanceSource.getProcessInstanceKey();
      if (operationType.equals(OperationType.RESOLVE_INCIDENT) && incidentId == null) {
        final List<Long> allIncidentKeys = incidentKeys.get(processInstanceKey);
        if (allIncidentKeys != null && !allIncidentKeys.isEmpty()) {
          for (Long incidentKey: allIncidentKeys) {
            OperationEntity operationEntity = createOperationEntity(processInstanceSource, operationType, batchOperationId)
              .setIncidentKey(incidentKey);
            batchRequest.add(operationTemplate.getFullQualifiedName(), operationEntity);
            operationsCount++;
          }
        }
      } else {
        OperationEntity operationEntity = createOperationEntity(processInstanceSource, operationType, batchOperationId)
          .setIncidentKey(toLongOrNull(incidentId));
        if(operationType == OperationType.MIGRATE_PROCESS_INSTANCE) {
          try {
            operationEntity.setMigrationPlan(objectMapper.writeValueAsString(batchOperationRequest.getMigrationPlan()));
          } catch (IOException e) {
            throw new PersistenceException(e);
          }
        }
        batchRequest.add(operationTemplate.getFullQualifiedName(), operationEntity);
        operationsCount++;
      }
      //update process instance
      final String processInstanceId = String.valueOf(processInstanceKey);
      final String indexForProcessInstance = getOrDefaultForNullValue(processInstanceIdToIndexName,
          processInstanceKey, listViewTemplate.getFullQualifiedName());
      final Map<String,Object> params = Map.of("batchOperationId", batchOperationId);
      final String script = "if (ctx._source.batchOperationIds == null){"
          + "ctx._source.batchOperationIds = new String[]{params.batchOperationId};"
          + "} else {"
          + "ctx._source.batchOperationIds.add(params.batchOperationId);"
          + "}";
      batchRequest.updateWithScript(indexForProcessInstance, processInstanceId, script, params);
    }

    batchRequest.execute();
    return operationsCount;
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
