/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.util;

import static io.camunda.tasklist.util.TestCheck.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphql.spring.boot.test.GraphQLResponse;
import com.graphql.spring.boot.test.GraphQLTestTemplate;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.graphql.TaskIT;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.tasklist.webapp.security.oauth.IdentityJwt2AuthenticationTokenConverter;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.MigrationPlanBuilderImpl;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.Protocol;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

@Component
@Scope(SCOPE_PROTOTYPE)
public class TasklistTester {

  private static final String GRAPHQL_DEFAULT_VARIABLE_FRAGMENT =
      "graphql/variableIT/full-variable-fragment.graphql";
  private static final String TASK_RESULT_PATTERN = "{id name assignee taskState completionTime}";
  private static final String COMPLETE_TASK_MUTATION_PATTERN =
      "mutation {completeTask(taskId: \"%s\", variables: %s)" + TASK_RESULT_PATTERN + "}";
  private static final String CLAIM_TASK_MUTATION_PATTERN =
      "mutation {claimTask(taskId: \"%s\")" + TASK_RESULT_PATTERN + "}";
  private static final String VARIABLE_INPUT_PATTERN = "{name: \"%s\", value: \"%s\"}";

  private ZeebeClient zeebeClient;
  private DatabaseTestExtension databaseTestExtension;
  private JwtDecoder jwtDecoder;
  //
  private String processDefinitionKey;
  private String processInstanceId;
  private String taskId;

  @Autowired
  @Qualifier(PROCESS_IS_DEPLOYED_CHECK)
  private TestCheck processIsDeployedCheck;

  @Autowired
  @Qualifier(PROCESS_IS_DELETED_CHECK)
  private TestCheck processIsDeletedCheck;

  @Autowired
  @Qualifier(PROCESS_INSTANCE_IS_CANCELED_CHECK)
  private TestCheck processInstanceIsCanceledCheck;

  @Autowired
  @Qualifier(PROCESS_INSTANCE_IS_COMPLETED_CHECK)
  private TestCheck processInstanceIsCompletedCheck;

  @Autowired
  @Qualifier(TASK_IS_CREATED_BY_FLOW_NODE_BPMN_ID_CHECK)
  private TestCheck taskIsCreatedCheck;

  @Autowired
  @Qualifier(TASK_HAS_CANDIDATE_USERS)
  private TestCheck taskHasCandidateUsers;

  @Autowired
  @Qualifier(TASKS_ARE_CREATED_BY_FLOW_NODE_BPMN_ID_CHECK)
  private TestCheck tasksAreCreatedCheck;

  @Autowired
  @Qualifier(TASK_IS_CANCELED_BY_FLOW_NODE_BPMN_ID_CHECK)
  private TestCheck taskIsCanceledCheck;

  @Autowired
  @Qualifier(TASK_IS_COMPLETED_BY_FLOW_NODE_BPMN_ID_CHECK)
  private TestCheck taskIsCompletedCheck;

  @Autowired
  @Qualifier(TASK_IS_ASSIGNED_CHECK)
  private TestCheck taskIsAssignedCheck;

  @Autowired
  @Qualifier(TASK_VARIABLE_EXISTS_CHECK)
  private TestCheck taskVariableExists;

  @Autowired
  @Qualifier(VARIABLES_EXIST_CHECK)
  private TestCheck variablesExist;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private NoSqlHelper noSqlHelper;

  @Autowired private GraphQLTestTemplate graphQLTestTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired(required = false)
  private IdentityJwt2AuthenticationTokenConverter jwtAuthenticationConverter;

  private GraphQLResponse graphQLResponse;

  //
  //  private boolean operationExecutorEnabled = true;
  //
  //  private Long jobKey;
  //
  public TasklistTester(ZeebeClient zeebeClient, DatabaseTestExtension elasticsearchTestRule) {
    this.zeebeClient = zeebeClient;
    this.databaseTestExtension = elasticsearchTestRule;
  }

  public TasklistTester(
      ZeebeClient zeebeClient, DatabaseTestExtension elasticsearchTestRule, JwtDecoder jwtDecoder) {
    this(zeebeClient, elasticsearchTestRule);
    this.jwtDecoder = jwtDecoder;
  }

  //
  //  public Long getProcessInstanceKey() {
  //    return processInstanceKey;
  //  }
  //
  public TasklistTester createAndDeploySimpleProcess(String processId, String flowNodeBpmnId) {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .userTask(flowNodeBpmnId)
            .endEvent()
            .done();
    processDefinitionKey = ZeebeTestUtil.deployProcess(zeebeClient, process, processId + ".bpmn");
    return this;
  }

  public TasklistTester createAndDeploySimpleProcessZeebeUserTask(
      String processId, String flowNodeBpmnId) {
    return createAndDeploySimpleProcessZeebeUserTask(processId, flowNodeBpmnId, null);
  }

  public TasklistTester createAndDeploySimpleProcessZeebeUserTask(
      String processId, String flowNodeBpmnId, String assignee) {

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .userTask(flowNodeBpmnId)
            .zeebeAssignee(assignee)
            .zeebeUserTask()
            .endEvent("end")
            .done();
    processDefinitionKey = ZeebeTestUtil.deployProcess(zeebeClient, process, processId + ".bpmn");
    return this;
  }

  public TasklistTester createAndDeploySimpleProcessWithZeebeUserTask(
      String processId, String flowNodeBpmnId) {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .userTask(flowNodeBpmnId)
            .zeebeUserTask()
            .endEvent()
            .done();
    processDefinitionKey = ZeebeTestUtil.deployProcess(zeebeClient, process, processId + ".bpmn");
    return this;
  }

  public TasklistTester createAndDeployProcess(BpmnModelInstance process) {
    processDefinitionKey =
        ZeebeTestUtil.deployProcess(
            zeebeClient, process, process.getDefinitions().getId() + ".bpmn");
    return this;
  }

  public TasklistTester migrateProcessInstance(String oldTaskMapping, String newTaskMapping) {
    zeebeClient
        .newMigrateProcessInstanceCommand(Long.valueOf(processInstanceId))
        .migrationPlan(
            new MigrationPlanBuilderImpl()
                .withTargetProcessDefinitionKey(Long.valueOf(processDefinitionKey))
                .addMappingInstruction(oldTaskMapping, newTaskMapping)
                .build())
        .send()
        .join();
    return this;
  }

  public TasklistTester createAndDeploySimpleProcessWithCandidateGroup(
      String processId, String flowNodeBpmnId, String candidateGroup) {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .userTask(flowNodeBpmnId)
            .zeebeCandidateGroups(candidateGroup)
            .endEvent()
            .done();
    processDefinitionKey = ZeebeTestUtil.deployProcess(zeebeClient, process, processId + ".bpmn");
    return this;
  }

  public TasklistTester createAndDeploySimpleProcessWithCandidateUser(
      String processId, String flowNodeBpmnId, String candidateUser) {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .userTask(flowNodeBpmnId)
            .zeebeCandidateUsers(candidateUser)
            .endEvent()
            .done();
    processDefinitionKey = ZeebeTestUtil.deployProcess(zeebeClient, process, processId + ".bpmn");
    return this;
  }

  public TasklistTester createAndDeploySimpleProcessWithAssignee(
      String processId, String flowNodeBpmnId, String user) {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .userTask(flowNodeBpmnId)
            .zeebeAssignee(user)
            .endEvent()
            .done();
    processDefinitionKey = ZeebeTestUtil.deployProcess(zeebeClient, process, processId + ".bpmn");
    return this;
  }

  public TasklistTester createAndDeploySimpleProcess(
      String processId, String flowNodeBpmnId, String tenantId) {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .userTask(flowNodeBpmnId)
            .endEvent()
            .done();
    processDefinitionKey =
        ZeebeTestUtil.deployProcess(zeebeClient, process, processId + ".bpmn", tenantId);
    return this;
  }

  public TasklistTester createCreatedAndCompletedTasks(
      String processId, String flowNodeBpmnId, int created, int completed) {
    createAndDeploySimpleProcess(processId, flowNodeBpmnId).waitUntil().processIsDeployed();
    // complete tasks
    for (int i = 0; i < completed; i++) {
      startProcessInstance(processId)
          .waitUntil()
          .taskIsCreated(flowNodeBpmnId)
          .and()
          .claimAndCompleteHumanTask(flowNodeBpmnId);
    }
    // start more process instances
    for (int i = 0; i < created; i++) {
      startProcessInstance(processId).waitUntil().taskIsCreated(flowNodeBpmnId);
    }
    return this;
  }

  public TasklistTester createFailedTasks(
      String processId, String flowNodeBpmnId, int numberOfTasks, int numberOfRetries)
      throws IOException {
    createAndDeploySimpleProcess(processId, flowNodeBpmnId).waitUntil().processIsDeployed();

    for (int i = 0; i < numberOfTasks; i++) {
      startProcessInstance(processId).waitUntil().taskIsCreated(flowNodeBpmnId);
    }

    ZeebeTestUtil.failTaskWithRetries(
        zeebeClient,
        Protocol.USER_TASK_JOB_TYPE,
        TestUtil.createRandomString(10),
        numberOfTasks,
        numberOfRetries,
        null);

    return this;
  }

  public GraphQLResponse getByQueryResource(String resource) throws IOException {
    graphQLResponse = graphQLTestTemplate.postForResource(resource);
    return graphQLResponse;
  }

  public GraphQLResponse getByQuery(String query) {
    graphQLResponse = graphQLTestTemplate.postMultipart(query, "{}");
    return graphQLResponse;
  }

  public GraphQLResponse getTaskByQuery(String query) {
    graphQLResponse = graphQLTestTemplate.postMultipart(query, "{}");
    return graphQLResponse;
  }

  public List<TaskDTO> getTasksByQuery(String query) {
    graphQLResponse = graphQLTestTemplate.postMultipart(query, "{}");
    return getTasksByPath("$.data.tasks");
  }

  public GraphQLResponse getGraphTasksByQuery(String query) {
    return graphQLTestTemplate.postMultipart(query, "{}");
  }

  public GraphQLResponse getTaskById(String taskId) throws IOException {
    return getTaskById(taskId, GRAPHQL_DEFAULT_VARIABLE_FRAGMENT);
  }

  public GraphQLResponse getTaskById(String taskId, String fragmentResource) throws IOException {
    final ObjectNode taskIdQ = objectMapper.createObjectNode().put("taskId", taskId);
    return graphQLTestTemplate.perform(
        "graphql/taskIT/get-task.graphql", taskIdQ, Collections.singletonList(fragmentResource));
  }

  public List<TaskDTO> getCreatedTasks() throws IOException {
    graphQLResponse =
        graphQLTestTemplate.postForResource("graphql/taskIT/get-created-tasks.graphql");
    return getTasksByPath("$.data.tasks");
  }

  public List<TaskDTO> getCompletedTasks() throws IOException {
    graphQLResponse =
        graphQLTestTemplate.postForResource("graphql/taskIT/get-completed-tasks.graphql");
    return getTasksByPath("$.data.tasks");
  }

  public GraphQLResponse getAllTasks() throws IOException {
    return getAllTasks(GRAPHQL_DEFAULT_VARIABLE_FRAGMENT);
  }

  public GraphQLResponse getAllTasks(String fragmentResource) throws IOException {
    final ObjectNode query = objectMapper.createObjectNode();
    query.putObject("query");
    return this.getTasksByQueryAsVariable(query, fragmentResource);
  }

  public GraphQLResponse getAllProcesses(String search) throws IOException {
    final ObjectNode query = objectMapper.createObjectNode().put("search", search);
    return this.getProcessesByQuery(query);
  }

  public GraphQLResponse getAllProcessesWithBearerAuth(String search, String token)
      throws IOException {
    final ObjectNode query = objectMapper.createObjectNode().put("search", search);
    return this.getProcessesByQueryWithBearerAuth(query, token);
  }

  public GraphQLResponse getProcessesByQuery(ObjectNode variables) throws IOException {
    graphQLResponse = graphQLTestTemplate.perform("graphql/get-processes.graphql", variables);
    return graphQLResponse;
  }

  public GraphQLResponse getProcessesByQueryWithBearerAuth(ObjectNode variables, String token)
      throws IOException {
    graphQLResponse =
        graphQLTestTemplate
            .withBearerAuth(token)
            .perform("graphql/get-processes.graphql", variables);
    return graphQLResponse;
  }

  public GraphQLResponse getTasksByQueryAsVariable(ObjectNode variables) throws IOException {
    return getTasksByQueryAsVariable(variables, GRAPHQL_DEFAULT_VARIABLE_FRAGMENT);
  }

  public GraphQLResponse getTasksByQueryAsVariable(ObjectNode variables, String fragmentResource)
      throws IOException {
    graphQLResponse =
        graphQLTestTemplate.perform(
            "graphql/get-tasks-by-query.graphql",
            variables,
            Collections.singletonList(fragmentResource));
    return graphQLResponse;
  }

  public GraphQLResponse getVariablesByTaskIdAndNames(ObjectNode variables) throws IOException {
    return getVariablesByTaskIdAndNames(variables, GRAPHQL_DEFAULT_VARIABLE_FRAGMENT);
  }

  public List<VariableDTO> getTaskVariables() throws IOException {
    assertThat(this.taskId).isNotNull();
    return getTaskVariablesByTaskId(this.taskId).getList("$.data.variables", VariableDTO.class);
  }

  public GraphQLResponse getTaskVariablesByTaskId(String taskId) throws IOException {
    final ObjectNode variablesQ = objectMapper.createObjectNode();
    variablesQ.put("taskId", taskId).putArray("variableNames");
    return getVariablesByTaskIdAndNames(variablesQ);
  }

  public GraphQLResponse getVariablesByTaskIdAndNames(ObjectNode variables, String fragmentResource)
      throws IOException {
    graphQLResponse =
        graphQLTestTemplate.perform(
            "graphql/variableIT/get-variables.graphql",
            variables,
            Collections.singletonList(fragmentResource));
    return graphQLResponse;
  }

  public GraphQLResponse getVariableById(String variableId) throws IOException {
    final ObjectNode variableQ = objectMapper.createObjectNode().put("variableId", variableId);
    graphQLResponse =
        graphQLTestTemplate.perform(
            "graphql/variableIT/get-variable.graphql",
            variableQ,
            Collections.singletonList(GRAPHQL_DEFAULT_VARIABLE_FRAGMENT));
    return graphQLResponse;
  }

  public GraphQLResponse getVariableById(String variableId, String fragmentResource)
      throws IOException {
    final ObjectNode variableQ = objectMapper.createObjectNode().put("variableId", variableId);
    graphQLResponse =
        graphQLTestTemplate.perform(
            "graphql/variableIT/get-variable.graphql",
            variableQ,
            Collections.singletonList(fragmentResource));
    return graphQLResponse;
  }

  public List<TaskDTO> getTasksByPath(String path) {
    return graphQLResponse.getList(path, TaskDTO.class);
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> getByPath(String path) {
    return graphQLResponse.get(path, Map.class);
  }

  public String get(String path) {
    return graphQLResponse.get(path);
  }

  public GraphQLResponse getForm(String id) throws IOException {
    final ObjectNode args = objectMapper.createObjectNode();
    args.put("id", id).put("processDefinitionId", processDefinitionKey);
    graphQLResponse = graphQLTestTemplate.perform("graphql/formIT/get-form.graphql", args);
    return graphQLResponse;
  }

  public TasklistTester claimTask(String claimRequest) {
    getByQuery(claimRequest);
    return this;
  }

  public TasklistTester unclaimTask(String unclaimRequest) {
    getByQuery(unclaimRequest);
    return this;
  }

  public Boolean deleteProcessInstance(String processInstanceId) {
    final String mutation =
        String.format(
            "mutation { deleteProcessInstance(processInstanceId: \"%s\") }", processInstanceId);
    graphQLResponse = graphQLTestTemplate.postMultipart(mutation, "{}");
    return graphQLResponse.get("$.data.deleteProcessInstance", Boolean.class);
  }

  public GraphQLResponse startProcess(String processDefinitionId) {
    final String mutation =
        String.format(
            "mutation { startProcess (processDefinitionId: \"%s\"){id}} ", processDefinitionId);
    return graphQLTestTemplate.postMultipart(mutation, "{}");
  }

  public TasklistTester deployProcess(String... classpathResources) {
    processDefinitionKey = ZeebeTestUtil.deployProcess(zeebeClient, classpathResources);
    return this;
  }

  public TasklistTester deployProcessForTenant(String tenantId, String... classpathResources) {
    processDefinitionKey = ZeebeTestUtil.deployProcess(tenantId, zeebeClient, classpathResources);
    return this;
  }

  public TasklistTester deployProcess(BpmnModelInstance processModel, String resourceName) {
    processDefinitionKey = ZeebeTestUtil.deployProcess(zeebeClient, processModel, resourceName);
    return this;
  }

  public TasklistTester processIsDeployed() {
    databaseTestExtension.processAllRecordsAndWait(processIsDeployedCheck, processDefinitionKey);
    return this;
  }

  public TasklistTester processIsDeleted() {
    databaseTestExtension.processAllRecordsAndWait(processIsDeletedCheck, processDefinitionKey);
    return this;
  }

  public TasklistTester startProcessInstance(String bpmnProcessId) {
    return startProcessInstance(bpmnProcessId, null);
  }

  public TasklistTester startProcessInstances(String bpmnProcessId, Integer numberOfInstances) {
    IntStream.range(0, numberOfInstances).forEach(i -> startProcessInstance(bpmnProcessId));
    return this;
  }

  public TasklistTester startProcessInstance(String bpmnProcessId, String payload) {
    processInstanceId = ZeebeTestUtil.startProcessInstance(zeebeClient, bpmnProcessId, payload);
    return this;
  }

  public TasklistTester startProcessInstance(
      String tenantId, String bpmnProcessId, String payload) {
    processInstanceId =
        ZeebeTestUtil.startProcessInstance(tenantId, zeebeClient, bpmnProcessId, payload);
    return this;
  }

  //  public TasklistTester failTask(String taskName, String errorMessage) {
  //    jobKey = ZeebeTestUtil.failTask(zeebeClient, taskName, UUID.randomUUID().toString(),
  // 3,errorMessage);
  //    return this;
  //  }
  //
  //  public TasklistTester throwError(String taskName,String errorCode,String errorMessage) {
  //    ZeebeTestUtil.throwErrorInTask(zeebeClient, taskName, UUID.randomUUID().toString(), 1,
  // errorCode, errorMessage);
  //    return this;
  //  }
  //
  //  public TasklistTester incidentIsActive() {
  //    elasticsearchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, processInstanceKey);
  //    return this;
  //  }
  //

  public TasklistTester taskIsCreated(String flowNodeBpmnId) {
    databaseTestExtension.processAllRecordsAndWait(
        taskIsCreatedCheck, processInstanceId, flowNodeBpmnId);
    // update taskId
    resolveTaskId(flowNodeBpmnId, TaskState.CREATED);
    return this;
  }

  public TasklistTester taskHasCandidateUsers(String flowNodeBpmnId) {
    databaseTestExtension.processAllRecordsAndWait(
        taskHasCandidateUsers, processInstanceId, flowNodeBpmnId);
    // update taskId
    resolveTaskId(flowNodeBpmnId, TaskState.CREATED);
    return this;
  }

  public TasklistTester createZeebeUserTask(
      String bpmnProcessId, String flowNodeBpmnId, String assignee, int numberOfInstances) {
    return createAndDeploySimpleProcessZeebeUserTask(bpmnProcessId, flowNodeBpmnId, assignee)
        .processIsDeployed()
        .then()
        .startProcessInstances(bpmnProcessId, numberOfInstances)
        .then()
        .taskIsCreated(flowNodeBpmnId);
  }

  public TasklistTester createZeebeUserTask(
      String bpmnProcessId, String flowNodeBpmnId, int numberOfInstances) {
    return createZeebeUserTask(bpmnProcessId, flowNodeBpmnId, null, numberOfInstances);
  }

  public TasklistTester tasksAreCreated(String flowNodeBpmnId, int taskCount) {
    databaseTestExtension.processAllRecordsAndWait(tasksAreCreatedCheck, flowNodeBpmnId, taskCount);
    // update taskId
    resolveTaskId(flowNodeBpmnId, TaskState.CREATED);
    return this;
  }

  public TasklistTester taskIsCanceled(String flowNodeBpmnId) {
    databaseTestExtension.processAllRecordsAndWait(
        taskIsCanceledCheck, processInstanceId, flowNodeBpmnId);
    // update taskId
    resolveTaskId(flowNodeBpmnId, TaskState.CANCELED);
    return this;
  }

  public TasklistTester taskIsFailed(String flowNodeBpmnId) {
    databaseTestExtension.processAllRecordsAndWait(
        taskIsCanceledCheck, processInstanceId, flowNodeBpmnId);
    // update taskId
    resolveTaskId(flowNodeBpmnId, TaskState.FAILED);
    return this;
  }

  public TasklistTester processInstanceIsCanceled() {
    databaseTestExtension.processAllRecordsAndWait(
        processInstanceIsCanceledCheck, processInstanceId);
    return this;
  }

  public TasklistTester processInstanceIsCompleted() {
    databaseTestExtension.processAllRecordsAndWait(
        processInstanceIsCompletedCheck, processInstanceId);
    return this;
  }

  private void resolveTaskId(final String flowNodeBpmnId, final TaskState state) {
    try {
      final List<TaskEntity> tasks = noSqlHelper.getTask(processInstanceId, flowNodeBpmnId);
      final Optional<TaskEntity> teOptional =
          tasks.stream().filter(te -> state.equals(te.getState())).findFirst();
      if (teOptional.isPresent()) {
        taskId = teOptional.get().getId();
      } else {
        taskId = null;
      }
    } catch (Exception ex) {
      taskId = null;
    }
  }

  public TasklistTester taskIsCompleted(String flowNodeBpmnId) {
    databaseTestExtension.processAllRecordsAndWait(
        taskIsCompletedCheck, processInstanceId, flowNodeBpmnId);
    // update taskId
    resolveTaskId(flowNodeBpmnId, TaskState.COMPLETED);
    return this;
  }

  public TasklistTester taskIsAssigned(String taskId) {
    databaseTestExtension.processAllRecordsAndWait(taskIsAssignedCheck, taskId);
    return this;
  }

  public TasklistTester taskVariableExists(String varName) {
    databaseTestExtension.processAllRecordsAndWait(taskVariableExists, taskId, varName);
    return this;
  }

  public TasklistTester variablesExist(String[] varNames) {
    databaseTestExtension.processAllRecordsAndWait(variablesExist, varNames);
    return this;
  }

  public TasklistTester claimAndCompleteHumanTask(String flowNodeBpmnId, String... variables) {
    claimHumanTask(flowNodeBpmnId);

    return completeHumanTask(flowNodeBpmnId, variables);
  }

  public TasklistTester claimHumanTask(final String flowNodeBpmnId) {
    // resolve taskId, if not yet resolved
    if (taskId == null) {
      resolveTaskId(flowNodeBpmnId, TaskState.CREATED);
      if (taskId == null) {
        fail(
            String.format(
                "Cannot resolveTaskId for flowNodeBpmnId=%s processDefinitionKey=%s state=%s",
                flowNodeBpmnId, processDefinitionKey, TaskState.CREATED));
      }
    }
    claimTask(String.format(CLAIM_TASK_MUTATION_PATTERN, taskId));

    taskIsAssigned(taskId);

    return this;
  }

  public TasklistTester completeHumanTask(String flowNodeBpmnId, String... variables) {
    // resolve taskId, if not yet resolved
    if (taskId == null) {
      resolveTaskId(flowNodeBpmnId, TaskState.CREATED);
      if (taskId == null) {
        fail(
            String.format(
                "Cannot resolveTaskId for flowNodeBpmnId=%s processDefinitionKey=%s state=%s",
                flowNodeBpmnId, processDefinitionKey, TaskState.CREATED));
      }
    }

    final String variablesAsString =
        createVariablesList(variables).stream()
            .map(this::variableAsGraphqlInput)
            .collect(Collectors.joining(", ", "[", "]"));
    getByQuery(
        String.format(COMPLETE_TASK_MUTATION_PATTERN, taskId, variablesAsString));

    return taskIsCompleted(flowNodeBpmnId);
  }

  private List<VariableInputDTO> createVariablesList(String... variables) {
    assertThat(variables.length % 2).isEqualTo(0);
    final List<VariableInputDTO> result = new ArrayList<>();
    for (int i = 0; i < variables.length; i = i + 2) {
      result.add(new VariableInputDTO().setName(variables[i]).setValue(variables[i + 1]));
    }
    return result;
  }

  private String variableAsGraphqlInput(VariableInputDTO variable) {
    return String.format(
        VARIABLE_INPUT_PATTERN,
        variable.getName(),
        StringEscapeUtils.escapeJson(variable.getValue()));
  }

  public TasklistTester completeUserTaskInZeebe() {
    ZeebeTestUtil.completeTask(
        zeebeClient, Protocol.USER_TASK_JOB_TYPE, TestUtil.createRandomString(10), null);
    return this;
  }

  public TasklistTester cancelProcessInstance() {
    ZeebeTestUtil.cancelProcessInstance(zeebeClient, Long.parseLong(processInstanceId));
    return this;
  }

  public TasklistTester deleteResource(String resourceKey) {
    ZeebeTestUtil.deleteResource(zeebeClient, Long.valueOf(resourceKey));
    return this;
  }

  public TasklistTester and() {
    return this;
  }

  public TasklistTester then() {
    return this;
  }

  public TasklistTester having() {
    return this;
  }

  public TasklistTester when() {
    return this;
  }

  public TasklistTester waitUntil() {
    return this;
  }

  public TasklistTester waitFor(long milliseconds) {
    ThreadUtil.sleepFor(milliseconds);
    return this;
  }

  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public String getTaskId() {
    return taskId;
  }

  public TasklistTester withAuthenticationToken(String token) {
    final Jwt jwt;
    try {
      jwt = jwtDecoder.decode(token);
    } catch (JwtException e) {
      throw new RuntimeException(e);
    }
    SecurityContextHolder.getContext().setAuthentication(jwtAuthenticationConverter.convert(jwt));
    return this;
  }

  public TasklistTester unsetAuthorization() {
    SecurityContextHolder.getContext().setAuthentication(null);
    return this;
  }
}
