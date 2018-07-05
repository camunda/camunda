package org.camunda.operate.es.writer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.camunda.operate.entities.ActivityInstanceEntity;
import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.es.types.EventType;
import org.camunda.operate.es.types.StrictTypeMappingCreator;
import org.camunda.operate.es.types.WorkflowInstanceType;
import org.camunda.operate.es.types.WorkflowType;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;


@Component
@Configuration
public class ElasticsearchRequestCreatorsHolder {

  private Logger logger = LoggerFactory.getLogger(ElasticsearchBulkProcessor.class);

  @Autowired
  @Qualifier("esObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired
  private TransportClient esClient;

  @Autowired
  private WorkflowType workflowType;

  @Autowired
  private EventType eventType;

  @Autowired
  private WorkflowInstanceType workflowInstanceType;

  /**
   * Insert or update workflow instance (UPSERT).
   * @return
   */
  public ElasticsearchRequestCreator<WorkflowInstanceEntity> workflowInstanceEsRequestCreator() {
    return (bulkRequestBuilder, entity) -> {
      try {

        Map<String, Object> updateFields = new HashMap<>();
        if (entity.getEndDate() != null) {
          updateFields.put(WorkflowInstanceType.END_DATE, entity.getEndDate());
        }
        updateFields.put(WorkflowInstanceType.STATE, entity.getState());
        updateFields.put(StrictTypeMappingCreator.PARTITION_ID, entity.getPartitionId());
        updateFields.put(StrictTypeMappingCreator.POSITION, entity.getPosition());

        //TODO some weird not efficient magic is needed here, in order to format date fields properly, may be this can be improved
        Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);

        final UpdateRequestBuilder updateRequest =
          esClient
            .prepareUpdate(workflowInstanceType.getType(), workflowInstanceType.getType(), entity.getId())
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
   * Inserting or updating an incident always mean UPDATE for workflow instance index.
   * In case the workflow instance is not there yet, we don't want to insert it (this is an exceptional situation,
   * as we process all events in one thread in ordered manner at the moment).
   *
   * @return
   */
  public ElasticsearchRequestCreator<IncidentEntity> incidentEsRequestCreator() {
    return (bulkRequestBuilder, entity) -> {
      try {

        Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(entity), HashMap.class);
        Map<String, Object> params = new HashMap<>();
        params.put("incident", jsonMap);
        params.put("partitionId", entity.getPartitionId());
        params.put("position", entity.getPosition());

        String script =
            "boolean f = false;" +
            "ctx._source.partitionId = params.partitionId; " +
            "ctx._source.position = params.position; " +
            "for (int j = 0; j < ctx._source.incidents.size(); j++) {" +
              "if (ctx._source.incidents[j].id == params.incident.id) {" +
                "ctx._source.incidents[j].state = params.incident.state;" +
                "f = true;" +
                "break;" +
              "}" +
            "}" +
            "if (!f) {" +
              "ctx._source.incidents.add(params.incident);"
                +
            "}";

        Script updateScript = new Script(
          ScriptType.INLINE,
          Script.DEFAULT_SCRIPT_LANG,
          script,
          params
        );
        return bulkRequestBuilder.add(
          esClient
            .prepareUpdate(workflowInstanceType.getType(), workflowInstanceType.getType(), entity.getWorkflowInstanceId())
            .setScript(updateScript));
      } catch (IOException e) {
        logger.error("Error preparing the query to update incident", e);
        throw new PersistenceException(String.format("Error preparing the query to update incident [%s]", entity.getId()), e);
      }
    };
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

        Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(entity), HashMap.class);
        Map<String, Object> params = new HashMap<>();
        params.put("activity", jsonMap);
        params.put("partitionId", entity.getPartitionId());
        params.put("position", entity.getPosition());

        String script =
            "boolean f = false;" +
            "ctx._source.partitionId = params.partitionId; " +
            "ctx._source.position = params.position; " +
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
              "ctx._source.activities.add(params.activity);"
                +
            "}";

        Script updateScript = new Script(
          ScriptType.INLINE,
          Script.DEFAULT_SCRIPT_LANG,
          script,
          params
        );
        return bulkRequestBuilder.add(
          esClient
            .prepareUpdate(workflowInstanceType.getType(), workflowInstanceType.getType(), entity.getWorkflowInstanceId())
            .setScript(updateScript));
      } catch (IOException e) {
        logger.error("Error preparing the query to update activity instance", e);
        throw new PersistenceException(String.format("Error preparing the query to update activity instance [%s]", entity.getId()), e);
      }
    };
  }

  /**
   *
   * @return
   */
  public ElasticsearchRequestCreator<WorkflowEntity> workflowEsRequestCreator() {
    return (bulkRequestBuilder, entity) -> {
      try {
        return bulkRequestBuilder.add(
          esClient
            .prepareIndex(workflowType.getType(), workflowType.getType(), entity.getId())
            .setSource(objectMapper.writeValueAsString(entity), XContentType.JSON)
        );
      } catch (JsonProcessingException e) {
        logger.error("Error preparing the query to insert workflow", e);
        throw new PersistenceException(String.format("Error preparing the query to insert workflow [%s]", entity.getId()), e);
      }
    };
  }

  public ElasticsearchRequestCreator<EventEntity> eventEsRequestCreator() {
    return (bulkRequestBuilder, entity) -> {
      try {
        return bulkRequestBuilder.add(
          esClient
            .prepareIndex(eventType.getType(), eventType.getType(), entity.getId())
            .setSource(objectMapper.writeValueAsString(entity), XContentType.JSON)
        );
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
    return map;
  }

}
