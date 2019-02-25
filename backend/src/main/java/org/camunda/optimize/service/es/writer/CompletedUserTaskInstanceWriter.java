package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.text.StringSubstitutor;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.UserTaskInstanceDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.END_DATE;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.START_DATE;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_OPERATIONS;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASKS;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASK_ACTIVITY_ID;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASK_ACTIVITY_INSTANCE_ID;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASK_DELETE_REASON;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASK_DUE_DATE;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASK_END_DATE;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASK_IDLE_DURATION;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASK_START_DATE;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASK_TOTAL_DURATION;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASK_WORK_DURATION;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;

@Component
public class CompletedUserTaskInstanceWriter {
  private static final Logger logger = LoggerFactory.getLogger(CompletedUserTaskInstanceWriter.class);

  private RestHighLevelClient esClient;
  private ObjectMapper objectMapper;

  @Autowired
  public CompletedUserTaskInstanceWriter(final RestHighLevelClient esClient,
                                         final ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
  }

  public void importUserTaskInstances(final List<UserTaskInstanceDto> userTaskInstances) throws Exception {
    logger.debug("Writing [{}] completed user task instances to elasticsearch", userTaskInstances.size());

    final BulkRequest userTaskToProcessInstanceBulkRequest = new BulkRequest();
    userTaskToProcessInstanceBulkRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

    final Map<String, List<UserTaskInstanceDto>> userTasksByProcessInstance =
      userTaskInstances.stream().collect(groupingBy(UserTaskInstanceDto::getProcessInstanceId));

    for (Map.Entry<String, List<UserTaskInstanceDto>> processInstanceEntry : userTasksByProcessInstance.entrySet()) {
      addActivityInstancesToProcessInstanceRequest(
        userTaskToProcessInstanceBulkRequest,
        processInstanceEntry.getKey(),
        processInstanceEntry.getValue()
      );
    }

    BulkResponse bulkResponse = esClient.bulk(userTaskToProcessInstanceBulkRequest, RequestOptions.DEFAULT);
    if (bulkResponse.hasFailures()) {
      String errorMessage = String.format(
        "There were failures while writing completed user task instances with message: {}",
        bulkResponse.buildFailureMessage()
      );
      throw new OptimizeRuntimeException(errorMessage);
    }

  }

  private void addActivityInstancesToProcessInstanceRequest(final BulkRequest bulkRequest,
                                                            final String processInstanceId,
                                                            final List<UserTaskInstanceDto> userTasks)
    throws IOException {

    final ImmutableMap<String, Object> scriptParameters = ImmutableMap.of(USER_TASKS, mapToParameterSet(userTasks));
    final Script updateScript = new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      createInlineUpdateScript(),
      scriptParameters
    );

    final UserTaskInstanceDto firstUserTaskInstance = userTasks.stream().findFirst()
      .orElseThrow(() -> new OptimizeRuntimeException("No user tasks to import provided"));
    final ProcessInstanceDto procInst = new ProcessInstanceDto();
    procInst.setProcessDefinitionId(firstUserTaskInstance.getProcessDefinitionId());
    procInst.setProcessDefinitionKey(firstUserTaskInstance.getProcessDefinitionKey());
    procInst.setProcessInstanceId(firstUserTaskInstance.getProcessInstanceId());
    procInst.getUserTasks().addAll(userTasks);
    procInst.setEngine(firstUserTaskInstance.getEngine());
    String newEntryIfAbsent = objectMapper.writeValueAsString(procInst);

    UpdateRequest request =
      new UpdateRequest(getOptimizeIndexAliasForType(PROC_INSTANCE_TYPE), PROC_INSTANCE_TYPE, processInstanceId)
        .script(updateScript)
        .upsert(newEntryIfAbsent, XContentType.JSON)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    bulkRequest.add(request);
  }

  private String createInlineUpdateScript() {
    // @formatter:off
    return
      "if (ctx._source.userTasks == null) ctx._source.userTasks = [];\n" +
      "def existingUserTasksById = ctx._source.userTasks.stream().collect(Collectors.toMap(task -> task.id, task -> task));\n" +
      "for (def newUserTask : params.userTasks) {\n" +
        "def existingTask  = existingUserTasksById.get(newUserTask.id);\n" +
        "if (existingTask != null) {\n" +
          createUpdateFieldsScript(ImmutableSet.of(
                USER_TASK_ACTIVITY_ID, USER_TASK_ACTIVITY_INSTANCE_ID,
                USER_TASK_TOTAL_DURATION, USER_TASK_WORK_DURATION, USER_TASK_IDLE_DURATION,
                USER_TASK_START_DATE, USER_TASK_END_DATE, USER_TASK_DUE_DATE, USER_TASK_DELETE_REASON
              )) +
        "} else {\n" +
          "if (ctx._source.userTasks == null) ctx._source.userTasks = [];\n" +
          "ctx._source.userTasks.add(newUserTask);\n" +
        "}\n" +
      "}\n"
      + createUpdateUserTaskMetricsScript()
      ;
    // @formatter:on
  }

  static String createUpdateUserTaskMetricsScript() {
    // @formatter:off
    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
      .put("userTasksField", USER_TASKS)
      .put("userOperationsField", USER_OPERATIONS)
      .put("startDateField", START_DATE)
      .put("endDateField", END_DATE)
      .put("idleDurationInMsField", USER_TASK_IDLE_DURATION)
      .put("workDurationInMsField", USER_TASK_WORK_DURATION)
      .put("totalDurationInMsField", USER_TASK_TOTAL_DURATION)
      .put("claimTypeValue", "Claim")
      .put("dateFormatPattern", OPTIMIZE_DATE_FORMAT)
      .build()
    );
    return substitutor.replace(
      "if (ctx._source.${userTasksField} != null) {\n" +
        "for (def currentTask : ctx._source.${userTasksField}) {\n" +
          // idle time defaults to 0, it get's eventually updated if a claim operation exists
          "currentTask.${idleDurationInMsField} = 0;\n" +
          // by default work duration equals total duration, it get's eventually updated if a claim operation exists
          "currentTask.${workDurationInMsField} = currentTask.${totalDurationInMsField};\n" +
          "if (currentTask.${userOperationsField} != null) {\n" +
            "def dateFormatter = new SimpleDateFormat(\"${dateFormatPattern}\");\n" +
            "def optionalFirstClaimDate = currentTask.${userOperationsField}.stream()\n" +
                ".filter(userOperation -> \"${claimTypeValue}\".equals(userOperation.type))\n" +
                ".map(userOperation -> userOperation.timestamp)\n" +
                ".map(dateFormatter::parse)\n" +
                ".min(Date::compareTo);\n" +
            "optionalFirstClaimDate.ifPresent(claimDate -> {\n" +
              "def claimDateInMs = claimDate.getTime();\n" +
              "def optionalStartDate = Optional.ofNullable(currentTask.${startDateField}).map(dateFormatter::parse);\n" +
              "def optionalEndDate = Optional.ofNullable(currentTask.${endDateField}).map(dateFormatter::parse);\n" +
              "optionalStartDate.ifPresent(startDate -> {\n" +
                  "currentTask.${idleDurationInMsField} = claimDateInMs - startDate.getTime();\n" +
              "});\n" +
              "optionalEndDate.ifPresent(endDate -> {\n" +
                  "currentTask.${workDurationInMsField} = endDate.getTime() - claimDateInMs;\n" +
              "});\n" +
            "});\n" +
          "}\n" +
        "}\n" +
      "}\n"
    );
    // @formatter:on
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, String>> mapToParameterSet(final List<UserTaskInstanceDto> userTaskInstanceDtos) {
    return userTaskInstanceDtos.stream()
      .map(userOperationDto -> (Map<String, String>) objectMapper.convertValue(userOperationDto, Map.class))
      .collect(Collectors.toList());
  }

  private String createUpdateFieldsScript(final Set<String> fieldKeys) {
    return fieldKeys
      .stream()
      .map(fieldKey -> String.format("existingTask.%s = newUserTask.%s;\n", fieldKey, fieldKey))
      .collect(Collectors.joining());
  }

}