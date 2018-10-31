package org.camunda.operate.zeebeimport;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.camunda.operate.entities.ActivityInstanceEntity;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.ActivityType;
import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.entities.EventSourceType;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.entities.SequenceFlowEntity;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.es.types.EventType;
import org.camunda.operate.es.types.StrictTypeMappingCreator;
import org.camunda.operate.es.types.WorkflowInstanceType;
import org.camunda.operate.es.types.WorkflowType;
import org.camunda.operate.es.writer.BatchOperationWriter;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.util.IdUtil;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@Configuration
public class ElasticsearchRequestCreatorsHolder {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchRequestCreatorsHolder.class);

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private TransportClient esClient;

  @Autowired
  private WorkflowType workflowType;

  @Autowired
  private EventType eventType;

  @Autowired
  private WorkflowInstanceType workflowInstanceType;

  @Autowired
  private BatchOperationWriter batchOperationWriter;

  @Autowired
  private WorkflowInstanceReader workflowInstanceReader;

  /**
   * Insert or update workflow instance (UPSERT).
   * @return
   */
  public ElasticsearchRequestCreator<WorkflowInstanceEntity> workflowInstanceEsRequestCreator() {
    return (bulkRequestBuilder, entity) -> {
      try {
        logger.debug("Workflow instance: id {}", entity.getId());
        Map<String, Object> updateFields = new HashMap<>();
        if (entity.getStartDate() != null) {
          updateFields.put(WorkflowInstanceType.START_DATE, entity.getStartDate());
        }
        if (entity.getEndDate() != null) {
          updateFields.put(WorkflowInstanceType.END_DATE, entity.getEndDate());
        }
        updateFields.put(WorkflowInstanceType.WORKFLOW_ID, entity.getWorkflowId());
        updateFields.put(WorkflowInstanceType.WORKFLOW_NAME, entity.getWorkflowName());
        updateFields.put(WorkflowInstanceType.WORKFLOW_VERSION, entity.getWorkflowVersion());
        updateFields.put(WorkflowInstanceType.STATE, entity.getState());
        updateFields.put(StrictTypeMappingCreator.PARTITION_ID, entity.getPartitionId());
        updateFields.put(StrictTypeMappingCreator.POSITION, entity.getPosition());
        updateFields.put(WorkflowInstanceType.STRING_VARIABLES, entity.getStringVariables());
        updateFields.put(WorkflowInstanceType.DOUBLE_VARIABLES, entity.getDoubleVariables());
        updateFields.put(WorkflowInstanceType.LONG_VARIABLES, entity.getLongVariables());
        updateFields.put(WorkflowInstanceType.BOOLEAN_VARIABLES, entity.getBooleanVariables());

        //TODO some weird not efficient magic is needed here, in order to format date fields properly, may be this can be improved
        Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);

        final UpdateRequestBuilder updateRequest =
          esClient
            .prepareUpdate(workflowInstanceType.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE, entity.getId())
            .setUpsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
            .setDoc(jsonMap);

        return bulkRequestBuilder.add(updateRequest);
      } catch (IOException e) {
        logger.error("Error preparing the query to upsert workflow instance", e);
        throw new PersistenceException(String.format("Error preparing the query to upsert workflow instance [%s]", entity.getId()), e);
      }
    };
  }

  /**
   * Inserting or updating an incident can be both insert or update for the workflow instance.
   *
   * @return
   */
  public ElasticsearchRequestCreator<IncidentEntity> incidentEsRequestCreator() {
    return (bulkRequestBuilder, entity) -> {
      try {
        logger.debug("Incident: id {}, workflowInstanceId {}", entity.getId(), entity.getWorkflowInstanceId());

        Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(entity), HashMap.class);
        Map<String, Object> params = new HashMap<>();
        params.put("incident", jsonMap);
        params.put("position", entity.getPosition());

        String script =
            "boolean f = false;" +
            "ctx._source.position = params.position; " +
            //search for incident
            "for (int j = 0; j < ctx._source.incidents.size(); j++) {" +
              "if (ctx._source.incidents[j].id == params.incident.id) {" +
              //update state of the incident
                "ctx._source.incidents[j].state = params.incident.state;" +
                "f = true;" +
                "break;" +
              "}" +
            "}" +
            "if (!f) {" +
              //add incident if not found
              "ctx._source.incidents.add(params.incident);" +
            "}" +

            //search for activity instance
            "for (int j = 0; j < ctx._source.activities.size(); j++) {" +
              "if (ctx._source.activities[j].id == params.incident.activityInstanceId) {" +
                "if (params.incident.state == '" + IncidentState.ACTIVE.toString() + "') {" +
                  "if (ctx._source.activities[j].type == '" + ActivityType.GATEWAY.toString() + "') {" +
                    "ctx._source.activities[j].state = '" + ActivityState.INCIDENT.toString() + "';" +
//                    "ctx._source.activities[j].endDate = null;" +   //TODO should be set by workflow instance event -> we need test case for this
                  "} else if (ctx._source.activities[j].state != '" + ActivityState.COMPLETED.toString() + "') {" +
                    "ctx._source.activities[j].state = '" + ActivityState.INCIDENT.toString() + "';" +
//                    "ctx._source.activities[j].endDate = null;" +   //TODO should be set by workflow instance event -> we need test case for this
                  "}" +
                "} else if (ctx._source.activities[j].type == '" + ActivityType.GATEWAY.toString() + "') {" +
                  "ctx._source.activities[j].state = '" + ActivityState.COMPLETED.toString() + "';" +
                "} else if (ctx._source.activities[j].state != '" + ActivityState.COMPLETED.toString() + "') {" +
                  "if (ctx._source.activities[j].endDate == null) {" +    //TODO test case for this -> 1st come Activity activates (+ Activity completed) event(s) and then Incident Created and Resolved
                    "ctx._source.activities[j].state = '" + ActivityState.ACTIVE.toString() + "';" +
                  "} else { " +
                    "ctx._source.activities[j].state = '" + ActivityState.COMPLETED.toString() + "';" +
                  "}" +
                "}" +
              "}" +
            "}";

        Script updateScript = new Script(
          ScriptType.INLINE,
          Script.DEFAULT_SCRIPT_LANG,
          script,
          params
        );

        //in case workflow instance was not loaded yet, we need to create new one
        WorkflowInstanceEntity workflowInstanceEntity = createWorkflowInstanceEntity(entity);

        return bulkRequestBuilder.add(
          esClient
            .prepareUpdate(workflowInstanceType.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE, entity.getWorkflowInstanceId())
            .setScript(updateScript)
            .setUpsert(objectMapper.writeValueAsString(workflowInstanceEntity), XContentType.JSON));
      } catch (IOException e) {
        logger.error("Error preparing the query to update incident", e);
        throw new PersistenceException(String.format("Error preparing the query to update incident [%s]", entity.getId()), e);
      }
    };
  }

  private WorkflowInstanceEntity createWorkflowInstanceEntity(IncidentEntity incident) {
    WorkflowInstanceEntity workflowInstanceEntity = new WorkflowInstanceEntity();
    workflowInstanceEntity.setId(incident.getWorkflowInstanceId());
    workflowInstanceEntity.setPosition(incident.getPosition());
    workflowInstanceEntity.setPartitionId(incident.getPartitionId());
    workflowInstanceEntity.setKey(IdUtil.extractKey(incident.getWorkflowInstanceId()));
    workflowInstanceEntity.getIncidents().add(incident);
    return workflowInstanceEntity;
  }

  /**
   * Inserting or updating an activity instance always mean UPDATE for workflow instance index.
   * In case the workflow instance is not there yet, we don't want to insert it (this is an exceptional situation,
   * as we process all events in one thread in ordered manner at the moment).
   *
   * @return
   */
  public ElasticsearchRequestCreator<ActivityInstanceEntity> activityInstanceEsRequestCreator() {
    return (bulkRequestBuilder, entity) -> {
      try {

        logger.debug("Activity instance: id {}, workflowInstanceId {}", entity.getId(), entity.getWorkflowInstanceId());

        Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(entity), HashMap.class);
        Map<String, Object> params = new HashMap<>();
        params.put("activity", jsonMap);
        params.put("position", entity.getPosition());

        String script =
            "boolean f = false;" +
            "ctx._source.position = params.position; " +
            //search for active incident
            "for (int j = 0; j < ctx._source.incidents.size(); j++) {" +
              "if (ctx._source.incidents[j].activityInstanceId == params.activity.id && " +
                  "ctx._source.incidents[j].state == '" + IncidentState.ACTIVE.toString() + "') {" +
                "params.activity.state = '" + ActivityState.INCIDENT.toString() + "'; " +
              "} " +
            "} " +
            "for (int j = 0; j < ctx._source.activities.size(); j++) {" +
              "if (ctx._source.activities[j].id == params.activity.id) {" +
                "if (params.activity.endDate != null) {" +
                  "ctx._source.activities[j].endDate = params.activity.endDate;" +
                  "ctx._source.activities[j].state = params.activity.state;" +
                "}" +
                "f = true;" +
                "break;" +
              "}" +
            "}" +
            "if (!f) {" +
              "ctx._source.activities.add(params.activity);" +
            "}"

          ;

        Script updateScript = new Script(
          ScriptType.INLINE,
          Script.DEFAULT_SCRIPT_LANG,
          script,
          params
        );
        return bulkRequestBuilder.add(
          esClient
            .prepareUpdate(workflowInstanceType.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE, entity.getWorkflowInstanceId())
            .setScript(updateScript));
      } catch (IOException e) {
        logger.error("Error preparing the query to update activity instance", e);
        throw new PersistenceException(String.format("Error preparing the query to update activity instance [%s]", entity.getId()), e);
      }
    };
  }

  /**
   * Insert Workflow.
   * @return
   */
  public ElasticsearchRequestCreator<WorkflowEntity> workflowEsRequestCreator() {
    return (bulkRequestBuilder, entity) -> {
      try {
        logger.debug("Workflow: id {}, bpmnProcessId {}", entity.getId(), entity.getBpmnProcessId());

        //find workflow instances with empty workflow name and version
        final List<String> workflowInstanceIds = workflowInstanceReader.queryWorkflowInstancesWithEmptyWorkflowVersion(entity.getKey());
        for (String workflowInstanceId : workflowInstanceIds) {
          Map<String, Object> updateFields = new HashMap<>();
          updateFields.put(WorkflowInstanceType.WORKFLOW_NAME, entity.getName());
          updateFields.put(WorkflowInstanceType.WORKFLOW_VERSION, entity.getVersion());
          bulkRequestBuilder.add(esClient
            .prepareUpdate(workflowInstanceType.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE, workflowInstanceId)
            .setDoc(updateFields));
        }

        return bulkRequestBuilder.add(
          esClient
            .prepareIndex(workflowType.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE, entity.getId())
            .setSource(objectMapper.writeValueAsString(entity), XContentType.JSON)
        );
      } catch (JsonProcessingException e) {
        logger.error("Error preparing the query to insert workflow", e);
        throw new PersistenceException(String.format("Error preparing the query to insert workflow [%s]", entity.getId()), e);
      }
    };
  }

  public ElasticsearchRequestCreator<SequenceFlowEntity> sequenceFlowEsRequestCreator() {
    return (bulkRequestBuilder, entity) -> {
      try {

        logger.debug("Sequence flow: id {}, activityId {}, workflowInstanceId {}", entity.getId(), entity.getActivityId(), entity.getWorkflowInstanceId());

        Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(entity), HashMap.class);
        Map<String, Object> params = new HashMap<>();
        params.put("sequenceFlow", jsonMap);

        String script =
          "boolean f = false;" +
          "for (int j = 0; j < ctx._source.sequenceFlows.size(); j++) {" +
            "if (ctx._source.sequenceFlows[j].id == params.sequenceFlow.id) {" +
              "f = true;" +
              "break;" +
            "}" +
          "}" +
          "if (!f) {" +
            "ctx._source.sequenceFlows.add(params.sequenceFlow);" +
          "}";

        Script updateScript = new Script(
          ScriptType.INLINE,
          Script.DEFAULT_SCRIPT_LANG,
          script,
          params
        );
        return bulkRequestBuilder.add(
          esClient
            .prepareUpdate(workflowInstanceType.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE, entity.getWorkflowInstanceId())
            .setScript(updateScript));
      } catch (IOException e) {
        logger.error("Error preparing the query to insert sequence flow", e);
        throw new PersistenceException(String.format("Error preparing the query to insert sequence flow instance [%s]", entity.getId()), e);
      }

    };
  }

  public ElasticsearchRequestCreator<EventEntity> eventEsRequestCreator() {
    return (bulkRequestBuilder, entity) -> {
      try {

        logger.debug("Event: id {}, eventSourceType {}, eventType {}, workflowInstanceId {}",
          entity.getId(), entity.getEventSourceType(), entity.getEventType(), entity.getWorkflowInstanceId());

        //write event
        bulkRequestBuilder =
          bulkRequestBuilder.add(esClient.prepareIndex(eventType.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE, entity.getId())
          .setSource(objectMapper.writeValueAsString(entity), XContentType.JSON));

        //complete operation in workflow instance if needed
        if (entity.getEventSourceType().equals(EventSourceType.JOB) &&
          entity.getEventType().equals(org.camunda.operate.entities.EventType.RETRIES_UPDATED)) {
          bulkRequestBuilder = bulkRequestBuilder.add(batchOperationWriter.createOperationCompletedRequest(entity.getWorkflowInstanceId(), OperationType.UPDATE_RETRIES));
          //if we update smth, we need it to have affect at once
          bulkRequestBuilder.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        } else if (entity.getEventSourceType().equals(EventSourceType.WORKFLOW_INSTANCE) &&
          entity.getEventType().equals(org.camunda.operate.entities.EventType.ELEMENT_TERMINATED)){
          bulkRequestBuilder = bulkRequestBuilder.add(batchOperationWriter.createOperationCompletedRequest(entity.getWorkflowInstanceId(), OperationType.CANCEL));
          //if we update smth, we need it to have affect at once
          bulkRequestBuilder.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        }
        return bulkRequestBuilder;
      } catch (JsonProcessingException e) {
        logger.error("Error preparing the query to insert event", e);
        throw new PersistenceException(String.format("Error preparing the query to insert event [%s]", entity.getId()), e);
      }
    };
  }

  @Bean
  public Map<Class<? extends OperateEntity>, ElasticsearchRequestCreator> getEsRequestMapping() {
    Map<Class<? extends OperateEntity>, ElasticsearchRequestCreator> map = new HashMap<>();
    map.put(WorkflowInstanceEntity.class, workflowInstanceEsRequestCreator());
    map.put(IncidentEntity.class, incidentEsRequestCreator());
    map.put(ActivityInstanceEntity.class, activityInstanceEsRequestCreator());
    map.put(WorkflowEntity.class, workflowEsRequestCreator());
    map.put(EventEntity.class, eventEsRequestCreator());
    map.put(SequenceFlowEntity.class, sequenceFlowEsRequestCreator());
    return map;
  }

}
