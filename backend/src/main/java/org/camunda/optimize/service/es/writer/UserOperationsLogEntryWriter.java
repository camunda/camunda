package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.camunda.optimize.dto.optimize.importing.UserOperationDto;
import org.camunda.optimize.dto.optimize.importing.UserOperationLogEntryDto;
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.writer.CompletedUserTaskInstanceWriter.createUpdateUserTaskMetricsScript;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.USER_TASK_INSTANCE_TYPE;

@Component
public class UserOperationsLogEntryWriter {
  private static final Logger logger = LoggerFactory.getLogger(UserOperationsLogEntryWriter.class);

  private final RestHighLevelClient esClient;
  private final ObjectMapper objectMapper;

  @Autowired
  public UserOperationsLogEntryWriter(final RestHighLevelClient esClient,
                                      final ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
  }

  public void importUserOperationLogEntries(final List<UserOperationLogEntryDto> userOperationLogEntries) throws
                                                                                                          Exception {
    logger.debug("Writing [{}] user operation log entries to elasticsearch", userOperationLogEntries.size());

    final BulkRequest userOperationsBulkRequest = new BulkRequest();
    userOperationsBulkRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

    final Map<String, Set<UserOperationLogEntryDto>> userOperationsByUserTask = userOperationLogEntries.stream()
      .collect(groupingBy(UserOperationLogEntryDto::getUserTaskId, toSet()));

    for (Map.Entry<String, Set<UserOperationLogEntryDto>> taskIdOperationsEntry : userOperationsByUserTask.entrySet()) {
      addImportUserOperationsLogEntryRequest(
        userOperationsBulkRequest, taskIdOperationsEntry.getKey(), taskIdOperationsEntry.getValue()
      );
    }

    final BulkResponse bulkResponse = esClient.bulk(userOperationsBulkRequest, RequestOptions.DEFAULT);
    if (bulkResponse.hasFailures()) {
      throw new OptimizeRuntimeException(
        "There were failures while writing user operation log entries with message: "
          + bulkResponse.buildFailureMessage()
      );
    }
  }

  private void addImportUserOperationsLogEntryRequest(final BulkRequest bulkRequest,
                                                      final String userTaskId,
                                                      final Set<UserOperationLogEntryDto> userOperationLogEntries)
    throws JsonProcessingException {

    final UserOperationLogEntryDto firstUserLogEntry = userOperationLogEntries.stream().findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Expected at least one user operation log entry"));
    final Set<UserOperationDto> userOperationDtos = mapToUserOperationDtos(userOperationLogEntries);

    final Script updateScript = createUpdateUserOperationsScript(userOperationDtos);
    final String newUserTaskEntryIfAbsent = objectMapper.writeValueAsString(
      new UserTaskInstanceDto(userTaskId, userOperationDtos, firstUserLogEntry.getEngineAlias())
    );

    final UpdateRequest request =
      new UpdateRequest(getOptimizeIndexAliasForType(USER_TASK_INSTANCE_TYPE), USER_TASK_INSTANCE_TYPE, userTaskId)
        .script(updateScript)
        .upsert(newUserTaskEntryIfAbsent, XContentType.JSON)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    bulkRequest.add(request);
  }

  private Set<UserOperationDto> mapToUserOperationDtos(final Set<UserOperationLogEntryDto> userOperationLogEntries) {
    return userOperationLogEntries.stream()
      .map(userOperationLogEntryDto -> new UserOperationDto(
        userOperationLogEntryDto.getId(),
        userOperationLogEntryDto.getUserId(),
        userOperationLogEntryDto.getTimestamp(),
        userOperationLogEntryDto.getOperationType(),
        userOperationLogEntryDto.getProperty(),
        userOperationLogEntryDto.getOriginalValue(),
        userOperationLogEntryDto.getNewValue()
      ))
      .collect(Collectors.toSet());
  }

  private Script createUpdateUserOperationsScript(final Set<UserOperationDto> userOperationDtos) {
    return new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      // @formatter:off
      "HashMap userOperationsById = new HashMap();\n" +
      "if (ctx._source.userOperations != null) {\n" +
        "for (def existingUserOperation : ctx._source.userOperations) {\n" +
          "userOperationsById.put(existingUserOperation.id, existingUserOperation);\n" +
        "}\n" +
      "}\n" +
      "for (def newUserOperation : params.userOperations) {\n" +
        "userOperationsById.putIfAbsent(newUserOperation.id, newUserOperation);\n" +
      "}\n" +
      "ctx._source.userOperations = userOperationsById.values();\n"

       + createUpdateUserTaskMetricsScript(),
      // @formatter:on
      ImmutableMap.of(
        "userOperations", mapToParameterSet(userOperationDtos)
      )
    );
  }



  @SuppressWarnings("unchecked")
  private Set<Map<String, String>> mapToParameterSet(final Set<UserOperationDto> userOperationDtos) {
    return userOperationDtos.stream()
      .map(userOperationDto -> (Map<String, String>) objectMapper.convertValue(userOperationDto, Map.class))
      .collect(Collectors.toSet());
  }

}