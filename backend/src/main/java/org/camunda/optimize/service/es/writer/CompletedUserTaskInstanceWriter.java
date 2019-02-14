package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.UserTaskInstanceDto;
import org.camunda.optimize.service.es.schema.type.UserTaskInstanceType;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.USER_TASK_INSTANCE_TYPE;

@Component
public class CompletedUserTaskInstanceWriter {
  private static final Logger logger = LoggerFactory.getLogger(CompletedUserTaskInstanceWriter.class);

  private RestHighLevelClient esClient;
  private ObjectMapper objectMapper;
  private DateTimeFormatter dateTimeFormatter;

  @Autowired
  public CompletedUserTaskInstanceWriter(final RestHighLevelClient esClient,
                                         final ObjectMapper objectMapper,
                                         final DateTimeFormatter dateTimeFormatter) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
    this.dateTimeFormatter = dateTimeFormatter;
  }

  public void importUserTaskInstances(final List<UserTaskInstanceDto> userTaskInstances) throws Exception {
    logger.debug("Writing [{}] completed user task instances to elasticsearch", userTaskInstances.size());

    final BulkRequest userTaskBulkRequest = new BulkRequest();
    userTaskBulkRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

    for (UserTaskInstanceDto userTaskInstance : userTaskInstances) {
      addImportUserTaskInstanceRequest(userTaskBulkRequest, userTaskInstance);
    }
    BulkResponse bulkResponse = esClient.bulk(userTaskBulkRequest, RequestOptions.DEFAULT);
    if (bulkResponse.hasFailures()) {
      logger.warn(
        "There were failures while writing completed user task instances with message: {}",
        bulkResponse.buildFailureMessage()
      );
    }
  }

  private void addImportUserTaskInstanceRequest(final BulkRequest bulkRequest, final UserTaskInstanceDto userTask)
    throws JsonProcessingException {

    final String userTaskId = userTask.getId();
    final Script updateScript = createUpdateFieldsScript(userTask);
    final String newEntryIfAbsent = objectMapper.writeValueAsString(userTask);

    final UpdateRequest request =
      new UpdateRequest(getOptimizeIndexAliasForType(USER_TASK_INSTANCE_TYPE), USER_TASK_INSTANCE_TYPE, userTaskId)
        .script(updateScript)
        .upsert(newEntryIfAbsent, XContentType.JSON)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    bulkRequest.add(request);
  }

  private Script createUpdateFieldsScript(final UserTaskInstanceDto userTask) {
    // only fields for which the source of truth is the completed user task
    final Map<String, Object> params = new HashMap<>();
    params.put(UserTaskInstanceType.PROCESS_DEFINITION_ID, userTask.getProcessDefinitionId());
    params.put(UserTaskInstanceType.PROCESS_DEFINITION_KEY, userTask.getProcessDefinitionKey());
    params.put(UserTaskInstanceType.PROCESS_DEFINITION_VERSION, userTask.getProcessDefinitionVersion());

    params.put(UserTaskInstanceType.PROCESS_INSTANCE_ID, userTask.getProcessInstanceId());

    params.put(UserTaskInstanceType.ACTIVITY_ID, userTask.getActivityId());
    params.put(UserTaskInstanceType.ACTIVITY_INSTANCE_ID, userTask.getActivityInstanceId());

    params.put(UserTaskInstanceType.DURATION, userTask.getDurationInMs());

    params.put(UserTaskInstanceType.START_DATE, dateTimeFormatter.format(userTask.getStartDate()));
    params.put(UserTaskInstanceType.END_DATE, dateTimeFormatter.format(userTask.getEndDate()));

    Optional.ofNullable(userTask.getDueDate())
      .ifPresent(dueDate -> params.put(UserTaskInstanceType.DUE_DATE, dateTimeFormatter.format(dueDate)));

    params.put(UserTaskInstanceType.DELETE_REASON, userTask.getDeleteReason());

    params.put(UserTaskInstanceType.ENGINE, userTask.getEngine());

    return new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      params.keySet()
        .stream()
        .map(fieldKey -> String.format("ctx._source.%s = params.%s;\n", fieldKey, fieldKey))
        .collect(Collectors.joining()),
      params
    );
  }

}