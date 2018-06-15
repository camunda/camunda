package org.camunda.operate.es.writer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.es.types.WorkflowInstanceType;
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
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Svetlana Dorokhova.
 */
@Component
@Configuration
@Profile("elasticsearch")
public class ElasticsearchRequestCreatorsHolder {

  private Logger logger = LoggerFactory.getLogger(ElasticsearchBulkProcessor.class);

  @Autowired
  @Qualifier("esObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired
  private TransportClient esClient;

  /**
   * Insert or update workflow instance (UPSERT).
   * @return
   */
  public ElasticsearchRequestCreator<WorkflowInstanceEntity> workflowInstanceEntityEsRequestCreator() {
    return (bulkRequestBuilder, entity) -> {
      try {

        Map<String, Object> updateFields = new HashMap<>();
        if (entity.getEndDate() != null) {
          updateFields.put(WorkflowInstanceType.END_DATE, entity.getEndDate());
        }
        updateFields.put(WorkflowInstanceType.STATE, entity.getState());

        //TODO some weird not efficient magic is needed here, in order to format date fields properly, may be this can be improved
        Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);

        final UpdateRequestBuilder updateRequest =
          esClient
            .prepareUpdate(WorkflowInstanceType.TYPE, WorkflowInstanceType.TYPE, entity.getId())
            .setUpsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
            .setDoc(jsonMap);

        return bulkRequestBuilder.add(updateRequest);
      } catch (JsonProcessingException e) {
        //TODO
        logger.error("Error preparing the query to upsert workflow instance", e);
        return bulkRequestBuilder;
      } catch (IOException e) {
        //TODO
        logger.error("Error preparing the query to upsert workflow instance", e);
        return bulkRequestBuilder;
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
  public ElasticsearchRequestCreator<IncidentEntity> incidentEntityEsRequestCreator() {
    return (bulkRequestBuilder, entity) -> {
      try {

        Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(entity), HashMap.class);
        Map<String, Object> params = new HashMap<>();
        params.put("incident", jsonMap);

        String script =
            "boolean f = false;" +
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
            .prepareUpdate(WorkflowInstanceType.TYPE, WorkflowInstanceType.TYPE, entity.getWorkflowInstanceId())
            .setScript(updateScript));
      } catch (JsonProcessingException e) {
        //TODO
        logger.error("Error preparing the query to update incident", e);
        return bulkRequestBuilder;
      } catch (IOException e) {
        //TODO
        logger.error("Error preparing the query to update incident", e);
        return bulkRequestBuilder;
      }
    };
  }

  @Bean
  public Map<Class<? extends OperateEntity>, ElasticsearchRequestCreator> getEsRequestMapping() {
    Map<Class<? extends OperateEntity>, ElasticsearchRequestCreator> map = new HashMap<>();
    map.put(WorkflowInstanceEntity.class, workflowInstanceEntityEsRequestCreator());
    map.put(IncidentEntity.class, incidentEntityEsRequestCreator());
    return map;
  }

}
