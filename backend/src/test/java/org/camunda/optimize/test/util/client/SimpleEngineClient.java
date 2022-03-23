/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.util.client;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.std.DateSerializer;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.ImmutableSet;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.engine.DecisionDefinitionXmlEngineDto;
import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.engine.HistoricIncidentEngineDto;
import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.engine.HistoricUserTaskInstanceDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.dto.engine.TenantEngineDto;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.rest.engine.dto.DeploymentDto;
import org.camunda.optimize.rest.engine.dto.EngineUserDto;
import org.camunda.optimize.rest.engine.dto.ExecutionDto;
import org.camunda.optimize.rest.engine.dto.ExternalTaskEngineDto;
import org.camunda.optimize.rest.engine.dto.GroupDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.rest.engine.dto.UserCredentialsDto;
import org.camunda.optimize.rest.engine.dto.UserProfileDto;
import org.camunda.optimize.rest.optimize.dto.VariableDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.mapper.CustomOffsetDateTimeDeserializer;
import org.camunda.optimize.service.util.mapper.CustomOffsetDateTimeSerializer;
import org.camunda.optimize.test.engine.EnginePluginClient;
import org.camunda.optimize.test.it.extension.EngineVariableValue;
import org.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import org.camunda.optimize.test.util.client.dto.EngineIncidentDto;
import org.camunda.optimize.test.util.client.dto.MessageCorrelationDto;
import org.camunda.optimize.test.util.client.dto.TaskDto;
import org.camunda.optimize.test.util.client.dto.VariableValueDto;
import org.elasticsearch.common.io.Streams;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.awaitility.Awaitility.await;
import static org.camunda.optimize.service.util.importing.EngineConstants.ALL_PERMISSION;
import static org.camunda.optimize.service.util.importing.EngineConstants.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.importing.EngineConstants.OPTIMIZE_APPLICATION_RESOURCE_ID;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_APPLICATION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_GROUP;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_USER;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.util.BpmnModels.DEFAULT_TOPIC;

@Slf4j
public class SimpleEngineClient {

  private static final String DEFAULT_EMAIL_DOMAIN = "@camunda.org";
  private static final String DEFAULT_FIRSTNAME = "firstName";
  private static final String DEFAULT_LASTNAME = "lastName";
  private static final String DEFAULT_WORKER = "default worker";
  private static final String COUNT = "count";

  // @formatter:off
  private static final TypeReference<List<TaskDto>> TASK_LIST_TYPE_REFERENCE = new TypeReference<List<TaskDto>>() {};
  // @formatter:on
  private static final int MAX_CONNECTIONS = 150;
  private static final Set<String> STANDARD_USERS = ImmutableSet.of("mary", "john", "peter");
  private static final Set<String> STANDARD_GROUPS = ImmutableSet.of("accounting", "management", "sales");
  public static final String DELAY_VARIABLE_NAME = "delay";

  private final EnginePluginClient enginePluginClient;
  private final CloseableHttpClient client;
  private final String engineRestEndpoint;
  private final ObjectMapper objectMapper;
  private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(
    IntegrationTestConfigurationUtil.getEngineDateFormat());

  public SimpleEngineClient(String engineRestEndpoint) {
    this.engineRestEndpoint = engineRestEndpoint;
    client = createClient();
    enginePluginClient = new EnginePluginClient(client);
    objectMapper = createObjectMapper();
  }

  private CloseableHttpClient createClient() {
    return HttpClientBuilder.create()
      .setMaxConnPerRoute(MAX_CONNECTIONS)
      .setMaxConnTotal(MAX_CONNECTIONS)
      .build();
  }

  public void cleanEngine(String engineName) {
    enginePluginClient.cleanEngine(engineName);
  }

  public void deployEngine(String engineName) {
    enginePluginClient.deployEngine(engineName);
  }

  @SneakyThrows
  public void initializeStandardUserAndGroupAuthorizations() {
    STANDARD_USERS.forEach(this::grantUserOptimizeAllDefinitionAndTenantsAndIdentitiesAuthorization);
    STANDARD_GROUPS.forEach(this::grantGroupOptimizeAllDefinitionAndAllTenantsAuthorization);
  }

  private static ObjectMapper createObjectMapper() {
    final JavaTimeModule javaTimeModule = new JavaTimeModule();
    javaTimeModule.addSerializer(OffsetDateTime.class, new CustomOffsetDateTimeSerializer(dateTimeFormatter));
    javaTimeModule.addSerializer(Date.class, new DateSerializer(false, new StdDateFormat().withColonInTimeZone(false)));
    javaTimeModule.addDeserializer(OffsetDateTime.class, new CustomOffsetDateTimeDeserializer(dateTimeFormatter));

    return Jackson2ObjectMapperBuilder
      .json()
      .modules(javaTimeModule)
      .featuresToDisable(
        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
        DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE,
        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES
      )
      .featuresToEnable(
        JsonParser.Feature.ALLOW_COMMENTS,
        SerializationFeature.INDENT_OUTPUT
      )
      .build();
  }

  @SneakyThrows
  private String serializeDateTimeToUrlEncodedString(final OffsetDateTime createdAfter) {
    return URLEncoder.encode(dateTimeFormatter.format(createdAfter), StandardCharsets.UTF_8.name());
  }

  public List<String> deployProcesses(BpmnModelInstance modelInstance, int nVersions, List<String> tenants) {
    List<String> result = new ArrayList<>();
    IntStream.rangeClosed(1, nVersions)
      .mapToObj(n -> deployProcessAndGetIds(modelInstance, tenants))
      .collect(Collectors.toList()).forEach(result::addAll);
    return result;
  }

  public DeploymentDto deployDecision(DmnModelInstance dmnModelInstance, String tenantId) {
    String decisionDefinition = Dmn.convertToString(dmnModelInstance);
    HttpPost deploymentRequest = createDeploymentRequest(decisionDefinition, "test.dmn", tenantId);
    DeploymentDto deployment = new DeploymentDto();
    try (CloseableHttpResponse response = client.execute(deploymentRequest)) {
      if (response.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
        String responseErrorMessage = EntityUtils.toString(response.getEntity(), "UTF-8");
        String exceptionMessage = String.format(
          "Something really bad happened during deployment! Expected response code 200 but got [%d]. The following " +
            "message was given from the engine: \n%s",
          response.getStatusLine().getStatusCode(),
          responseErrorMessage
        );
        throw new OptimizeRuntimeException(exceptionMessage);
      }
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      deployment = objectMapper.readValue(responseString, DeploymentDto.class);
    } catch (IOException e) {
      log.error("Error during deployment request! Could not deploy the given decisionDefinition model!", e);
    }
    return deployment;
  }

  public EngineUserDto createEngineUserDto(final String username, final String password) {
    return createEngineUserDto(username, password, DEFAULT_FIRSTNAME, DEFAULT_LASTNAME);
  }

  public EngineUserDto createEngineUserDto(final String username,
                                           final String firstName,
                                           final String lastName) {
    return createEngineUserDto(
      username, username, username + DEFAULT_EMAIL_DOMAIN, firstName, lastName
    );
  }

  public EngineUserDto createEngineUserDto(final String username,
                                           final String password,
                                           final String firstName,
                                           final String lastName) {
    return createEngineUserDto(username, password, username + DEFAULT_EMAIL_DOMAIN, firstName, lastName);
  }

  private EngineUserDto createEngineUserDto(final String username,
                                            final String password,
                                            final String email,
                                            final String firstName,
                                            final String lastName) {
    final UserProfileDto profile = UserProfileDto.builder()
      .id(username)
      .email(email)
      .firstName(firstName)
      .lastName(lastName)
      .build();
    return new EngineUserDto(profile, new UserCredentialsDto(password));
  }

  @SneakyThrows
  public void createUser(final EngineUserDto userDto) {
    HttpPost createUserRequest = new HttpPost(engineRestEndpoint + "/user/create");
    createUserRequest.addHeader("Content-Type", "application/json");
    createUserRequest.setEntity(new StringEntity(objectMapper.writeValueAsString(userDto), StandardCharsets.UTF_8));

    try (CloseableHttpResponse createResponse = client.execute(createUserRequest)) {
      if (createResponse.getStatusLine().getStatusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
        log.warn("Failed to create user with id {}", userDto.getProfile().getId());
      }
    }
  }

  private void grantGroupOptimizeAllDefinitionAndAllTenantsAuthorization(final String groupId) {
    createGrantAllOfTypeGroupAuthorization(RESOURCE_TYPE_APPLICATION, groupId);
    createGrantAllOfTypeGroupAuthorization(RESOURCE_TYPE_PROCESS_DEFINITION, groupId);
    createGrantAllOfTypeGroupAuthorization(RESOURCE_TYPE_DECISION_DEFINITION, groupId);
    createGrantAllOfTypeGroupAuthorization(RESOURCE_TYPE_TENANT, groupId);
  }

  public void grantUserOptimizeAllDefinitionAndTenantsAndIdentitiesAuthorization(final String userId) {
    createGrantAllOfTypeUserAuthorization(RESOURCE_TYPE_APPLICATION, userId);
    createGrantAllOfTypeUserAuthorization(RESOURCE_TYPE_PROCESS_DEFINITION, userId);
    createGrantAllOfTypeUserAuthorization(RESOURCE_TYPE_DECISION_DEFINITION, userId);
    createGrantAllOfTypeUserAuthorization(RESOURCE_TYPE_TENANT, userId);
    createGrantAllOfTypeUserAuthorization(RESOURCE_TYPE_USER, userId);
    createGrantAllOfTypeUserAuthorization(RESOURCE_TYPE_GROUP, userId);
  }

  public void cleanUpDeployments() {
    log.info("Starting deployments clean up");
    try (final CloseableHttpResponse getDeploymentsResponse = client.execute(new HttpGet(getDeploymentUri()))) {
      final String responseString = EntityUtils.toString(getDeploymentsResponse.getEntity(), StandardCharsets.UTF_8);
      final List<DeploymentDto> result = objectMapper.readValue(
        responseString,
        new TypeReference<List<DeploymentDto>>() {
        }
      );
      log.info("Fetched " + result.size() + " deployments");

      for (final DeploymentDto deployment : result) {
        final HttpDelete delete = new HttpDelete(
          new URIBuilder(getDeploymentUri() + deployment.getId())
            .addParameter("cascade", "true")
            .build()
        );
        try (final CloseableHttpResponse deleteResponse = client.execute(delete)) {
          final int deleteStatusCode = deleteResponse.getStatusLine().getStatusCode();
          if (Response.Status.NO_CONTENT.getStatusCode() == deleteStatusCode) {
            log.info("Deleted deployment with id {}.", deployment.getId());
          } else {
            throw new OptimizeRuntimeException(String.format(
              "Deleting deployment with id %s failed with statusCode: %s.",
              deployment.getId(),
              deleteStatusCode
            ));
          }
        }
      }
      log.info("Deployment clean up finished");
    } catch (final Exception e) {
      log.error("Deployment clean up failed.", e);
      throw new OptimizeRuntimeException("Deployment clean up failed.", e);
    }

  }

  public Optional<Boolean> getProcessInstanceDelayVariable(String procInstId) {
    HttpGet get =
      new HttpGet(engineRestEndpoint + "/process-instance/" + procInstId + "/variables/" + DELAY_VARIABLE_NAME);
    VariableValueDto variable = new VariableValueDto();
    try (CloseableHttpResponse response = client.execute(get)) {
      if (response.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
        log.debug(String.format(
          "No variable [%s] found for process instance with ID [%s]",
          DELAY_VARIABLE_NAME,
          procInstId
        ));
        return Optional.empty();
      }
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      variable = objectMapper.readValue(responseString, VariableValueDto.class);
    } catch (IOException e) {
      log.error("Error while trying to fetch the variable!!", e);
    }
    return Optional.ofNullable(variable.getValue()).map(Object::toString).map(Boolean::parseBoolean);
  }

  public void suspendProcessInstance(final String processInstanceId) {
    HttpPut suspendRequest = new HttpPut(getSuspendProcessInstanceUri(processInstanceId));
    suspendRequest.setHeader("Content-type", "application/json");
    suspendRequest.setEntity(new StringEntity(
      "{\n" +
        "\"suspended\": true\n" +
        "}",
      StandardCharsets.UTF_8
    ));
    try (CloseableHttpResponse response = client.execute(suspendRequest)) {
      if (response.getStatusLine().getStatusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
        throw new RuntimeException(
          "Could not suspend process instance. Status-code: " + response.getStatusLine().getStatusCode()
        );
      }
    } catch (Exception e) {
      log.error("Error while trying to suspend process instance!");
      throw new RuntimeException(e);
    }
  }

  public void correlateMessage(String messageName) {
    HttpPost post = new HttpPost(engineRestEndpoint + "/message/");
    post.setHeader("Content-type", "application/json");
    MessageCorrelationDto message = new MessageCorrelationDto();
    message.setAll(true);
    message.setMessageName(messageName);
    StringEntity content;
    try {
      content = new StringEntity(objectMapper.writeValueAsString(message), StandardCharsets.UTF_8);
      post.setEntity(content);
      try (CloseableHttpResponse response = client.execute(post)) {
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != Response.Status.NO_CONTENT.getStatusCode()) {
          log.warn("Response code for correlating message should be 204, got " + statusCode + " instead");
          final String reponseBody = Streams.copyToString(new InputStreamReader(
            response.getEntity().getContent(), StandardCharsets.UTF_8
          ));
          log.warn("Response body was: " + reponseBody);
        }
      }
    } catch (Exception e) {
      log.warn("Error while trying to correlate message for name {}!", messageName);
    }
  }

  public UUID createIndependentUserTask() throws IOException {
    final UUID taskId = UUID.randomUUID();
    final HttpPost completePost = new HttpPost(engineRestEndpoint + "/task/create");
    completePost.setEntity(new StringEntity(
      String.format("{\"id\":\"%s\",\"name\":\"name\"}", taskId.toString())
    ));
    completePost.addHeader("Content-Type", "application/json");
    try (CloseableHttpResponse response = client.execute(completePost)) {
      if (response.getStatusLine().getStatusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
        throw new OptimizeRuntimeException(
          "Could not create user task! Status-code: " + response.getStatusLine().getStatusCode()
        );
      }
    }
    return taskId;
  }

  public List<TaskDto> getActiveTasksCreatedAfter(final String processDefinitionId,
                                                  final OffsetDateTime afterDateTime, final int limit) {
    HttpGet get = new HttpGet(getTaskListCreatedAfterUri(processDefinitionId, limit, afterDateTime));
    List<TaskDto> tasks = new ArrayList<>();
    try (CloseableHttpResponse response = client.execute(get)) {
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      tasks = objectMapper.readValue(responseString, TASK_LIST_TYPE_REFERENCE);
    } catch (IOException e) {
      log.error("Error while trying to fetch the user task!!", e);
    }
    return tasks;
  }

  public List<TaskDto> getActiveTasksCreatedOn(final String processDefinitionId,
                                               final OffsetDateTime creationDateTime) {
    HttpGet get = new HttpGet(getTaskListCreatedOnUri(processDefinitionId, creationDateTime));
    List<TaskDto> tasks = new ArrayList<>();
    try (CloseableHttpResponse response = client.execute(get)) {
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      tasks = objectMapper.readValue(responseString, TASK_LIST_TYPE_REFERENCE);
    } catch (IOException e) {
      log.error("Error while trying to fetch the user task!!", e);
    }
    return tasks;
  }

  public void setAssignee(final TaskDto task, final String userId) throws IOException {
    HttpPost claimPost = new HttpPost(getTaskAssigneeUri(task.getId()));
    claimPost.setEntity(new StringEntity(String.format(
      "{\"userId\" : %s}",
      Optional.ofNullable(userId).map(id -> "\"" + id + "\"").orElse(null)
    )));
    claimPost.addHeader("Content-Type", "application/json");
    try (CloseableHttpResponse claimResponse = client.execute(claimPost)) {
      if (claimResponse.getStatusLine().getStatusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
        throw new RuntimeException("Wrong error code when claiming user tasks!");
      }
    }
  }

  public void addOrRemoveCandidateGroupIdentityLinks(final TaskDto task, final String groupId) throws IOException {
    HttpGet identityLinksGet = new HttpGet(getTaskIdentityLinksUri(task.getId()));
    try (CloseableHttpResponse getLinksResponse = client.execute(identityLinksGet)) {
      String content = EntityUtils.toString(getLinksResponse.getEntity());
      List<JsonNode> links = objectMapper.readValue(content, new TypeReference<List<JsonNode>>() {
      });

      if (links.size() == 0) {
        HttpPost candidatePost = new HttpPost(getTaskIdentityLinksUri(task.getId()));
        candidatePost.setEntity(
          new StringEntity(String.format("{\"groupId\":\"%s\", \"type\":\"candidate\"}", groupId))
        );
        candidatePost.addHeader("Content-Type", "application/json");
        client.execute(candidatePost).close();
      } else {
        HttpPost candidateDeletePost = new HttpPost(getTaskIdentityLinksUri(task.getId()) + "/delete");
        candidateDeletePost.addHeader("Content-Type", "application/json");
        candidateDeletePost.setEntity(new StringEntity(objectMapper.writeValueAsString(links.get(0))));
        client.execute(candidateDeletePost).close();
      }
    }
  }

  public void addCandidateGroupForAllRunningUserTasks(final String processInstanceId,
                                                      final String groupId) {
    try (final CloseableHttpClient httpClient = createClientWithDefaultBasicAuth()) {
      final List<org.camunda.optimize.rest.engine.dto.TaskDto> tasks = getUserTasks(httpClient, processInstanceId);
      for (org.camunda.optimize.rest.engine.dto.TaskDto task : tasks) {
        addCandidateGroupToUserTask(httpClient, groupId, task.getId());
      }
    } catch (IOException e) {
      log.error("Error while trying to create http client auth authentication!", e);
    }
  }

  private void addCandidateGroupToUserTask(final CloseableHttpClient httpClient,
                                           final String groupId,
                                           final String taskId) throws IOException {
    HttpPost addCandidateGroupPost = new HttpPost(getTaskIdentityLinksUri(taskId));
    String bodyAsString = String.format("{\"groupId\": \"%s\", \"type\": \"candidate\"}", groupId);
    addCandidateGroupPost.setEntity(new StringEntity(bodyAsString));
    addCandidateGroupPost.addHeader("Content-Type", "application/json");
    try (CloseableHttpResponse response = httpClient.execute(addCandidateGroupPost)) {
      if (response.getStatusLine().getStatusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
        throw new OptimizeRuntimeException(
          "Could not add candidate group! Status-code: " + response.getStatusLine().getStatusCode()
        );
      }
    }
  }

  private List<org.camunda.optimize.rest.engine.dto.TaskDto> getUserTasks(final CloseableHttpClient authenticatingClient,
                                                                          final String processInstanceIdFilter) {
    final List<org.camunda.optimize.rest.engine.dto.TaskDto> tasks;
    try {
      final URIBuilder uriBuilder = new URIBuilder(getTaskListUri());
      if (processInstanceIdFilter != null) {
        uriBuilder.addParameter("processInstanceId", processInstanceIdFilter);
      }
      final HttpGet get = new HttpGet(uriBuilder.build());
      try (CloseableHttpResponse response = authenticatingClient.execute(get)) {
        String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
        // @formatter:off
        tasks = objectMapper.readValue(responseString, new TypeReference<List<org.camunda.optimize.rest.engine.dto.TaskDto>>() {});
        // @formatter:on
      } catch (IOException e) {
        throw new OptimizeRuntimeException("Error while trying to finish the user task!!");
      }
    } catch (URISyntaxException e) {
      throw new OptimizeRuntimeException("Error while trying to create task list url !!");
    }
    return tasks;
  }

  public void completeUserTask(TaskDto task) {
    HttpPost completePost = new HttpPost(getCompleteTaskUri(task.getId()));
    completePost.setEntity(new StringEntity("{}", StandardCharsets.UTF_8));
    completePost.addHeader("Content-Type", "application/json");
    try (CloseableHttpResponse response = client.execute(completePost)) {
      if (response.getStatusLine().getStatusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
        throw new RuntimeException("Wrong error code when completing user tasks!");
      }
    } catch (Exception e) {
      log.error("Could not complete user task!", e);
    }
  }

  public void completeUserTaskWithoutClaim(final String user, final String password, final String processInstanceId) {
    try (final CloseableHttpClient httpClient = createClientWithBasicAuth(user, password)) {
      final List<org.camunda.optimize.rest.engine.dto.TaskDto> tasks = getUserTasks(httpClient, processInstanceId);
      for (org.camunda.optimize.rest.engine.dto.TaskDto task : tasks) {
        completeUserTask(httpClient, task);
      }
    } catch (IOException e) {
      log.error("Error while trying to complete user task!", e);
    }
  }

  private void completeUserTask(final CloseableHttpClient authenticatingClient,
                                final org.camunda.optimize.rest.engine.dto.TaskDto task)
    throws IOException {
    HttpPost completePost = new HttpPost(getSecuredCompleteTaskUri(task.getId()));
    completePost.setEntity(new StringEntity("{}"));
    completePost.addHeader("Content-Type", "application/json");
    try (CloseableHttpResponse response = authenticatingClient.execute(completePost)) {
      if (response.getStatusLine().getStatusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
        throw new OptimizeRuntimeException(
          "Could not complete user task! Status-code: " + response.getStatusLine().getStatusCode()
        );
      }
    }
  }

  public List<String> deployDecisions(DmnModelInstance modelInstance, int nVersions, List<String> tenants) {
    List<String> result = new ArrayList<>();
    IntStream.rangeClosed(1, nVersions)
      .mapToObj(n -> deployDecisionAndGetIds(modelInstance, tenants))
      .collect(Collectors.toList()).forEach(result::addAll);
    return result;
  }

  private List<String> deployDecisionAndGetIds(DmnModelInstance modelInstance, List<String> tenants) {
    List<DeploymentDto> deploymentDto = deployDecisionDefinition(modelInstance, tenants);
    return deploymentDto.stream().map(this::getDecisionDefinitionId).collect(Collectors.toList());
  }

  private List<DeploymentDto> deployDecisionDefinition(DmnModelInstance dmnModelInstance, List<String> tenants) {
    String decision = Dmn.convertToString(dmnModelInstance);
    List<HttpPost> deploymentRequest = createDecisionDeploymentRequest(decision, tenants);
    return deploymentRequest.stream().map(d -> {
      DeploymentDto deployment = new DeploymentDto();
      try (CloseableHttpResponse response = client.execute(d)) {
        if (response.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
          throw new RuntimeException("Something really bad happened during deployment, " +
                                       "could not create a deployment!");
        }
        String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
        deployment = objectMapper.readValue(responseString, DeploymentDto.class);
      } catch (IOException e) {
        log.error("Error during deployment request! Could not deploy the given dmn model!", e);
      }
      return deployment;
    }).collect(Collectors.toList());
  }

  private List<String> deployProcessAndGetIds(BpmnModelInstance modelInstance, List<String> tenants) {
    List<DeploymentDto> deploymentDto = deployProcess(modelInstance, tenants);
    return deploymentDto.stream().map(this::getProcessDefinitionId).collect(Collectors.toList());
  }

  private String getDecisionDefinitionId(DeploymentDto deployment) {
    List<DecisionDefinitionEngineDto> decisionDefinitions = getAllDecisionDefinitions(deployment);
    if (decisionDefinitions.size() != 1) {
      log.warn("Deployment should contain only one decision definition!");
    }
    return decisionDefinitions.get(0).getId();
  }

  private List<DecisionDefinitionEngineDto> getAllDecisionDefinitions(DeploymentDto deployment) {
    HttpRequestBase get = new HttpGet(getDecisionDefinitionUri());
    URI uri = null;
    try {
      uri = new URIBuilder(get.getURI())
        .addParameter("deploymentId", deployment.getId())
        .build();
    } catch (URISyntaxException e) {
      log.error("Could not build uri!", e);
    }
    get.setURI(uri);
    List<DecisionDefinitionEngineDto> result = new ArrayList<>();
    try (CloseableHttpResponse response = client.execute(get)) {
      String responseString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
      result = objectMapper.readValue(
        responseString,
        new TypeReference<List<DecisionDefinitionEngineDto>>() {
        }
      );
    } catch (Exception e) {
      log.error("Could not fetch all decision definitions for given deployment!", e);
    }

    return result;
  }


  private String getProcessDefinitionId(DeploymentDto deployment) {
    List<ProcessDefinitionEngineDto> processDefinitions = getAllProcessDefinitions(deployment);
    if (processDefinitions.size() != 1) {
      log.warn("Deployment should contain only one process definition!");
    }
    return processDefinitions.get(0).getId();
  }

  private List<ProcessDefinitionEngineDto> getAllProcessDefinitions(DeploymentDto deployment) {
    HttpRequestBase get = new HttpGet(getProcessDefinitionUri());
    URI uri = null;
    try {
      uri = new URIBuilder(get.getURI())
        .addParameter("deploymentId", deployment.getId())
        .build();
    } catch (URISyntaxException e) {
      log.error("Could not build uri!", e);
    }
    get.setURI(uri);
    List<ProcessDefinitionEngineDto> result = new ArrayList<>();
    try (CloseableHttpResponse response = client.execute(get)) {
      String responseString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
      result = objectMapper.readValue(
        responseString,
        new TypeReference<List<ProcessDefinitionEngineDto>>() {
        }
      );
    } catch (Exception e) {
      log.error("Could not fetch all process definitions for given deployment!", e);
    }

    return result;
  }

  public List<ProcessDefinitionEngineDto> getLatestProcessDefinitions() {
    HttpRequestBase get = new HttpGet(getProcessDefinitionUri());
    URI uri = null;
    try {
      uri = new URIBuilder(get.getURI())
        .addParameter("latestVersion", "true")
        .build();
    } catch (URISyntaxException e) {
      log.error("Could not build uri!", e);
    }
    get.setURI(uri);
    List<ProcessDefinitionEngineDto> result = new ArrayList<>();
    try (CloseableHttpResponse response = client.execute(get)) {
      String responseString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
      result = objectMapper.readValue(
        responseString,
        new TypeReference<List<ProcessDefinitionEngineDto>>() {
        }
      );
    } catch (Exception e) {
      log.error("Could not fetch all process definitions for given deployment!", e);
    }

    return result;
  }

  public List<DecisionDefinitionEngineDto> getLatestDecisionDefinitions() {
    HttpRequestBase get = new HttpGet(getDecisionDefinitionUri());
    URI uri = null;
    try {
      uri = new URIBuilder(get.getURI())
        .addParameter("latestVersion", "true")
        .build();
    } catch (URISyntaxException e) {
      log.error("Could not build uri!", e);
    }
    get.setURI(uri);
    List<DecisionDefinitionEngineDto> result = new ArrayList<>();
    try (CloseableHttpResponse response = client.execute(get)) {
      String responseString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
      result = objectMapper.readValue(
        responseString,
        new TypeReference<List<DecisionDefinitionEngineDto>>() {
        }
      );
    } catch (Exception e) {
      log.error("Could not fetch all decision definitions for given deployment!", e);
    }

    return result;
  }

  public ProcessDefinitionXmlEngineDto getProcessDefinitionXml(String processDefinitionId) {
    HttpRequestBase get = new HttpGet(getProcessDefinitionXmlUri(processDefinitionId));
    CloseableHttpResponse response;
    try {
      response = client.execute(get);
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      ProcessDefinitionXmlEngineDto xml =
        objectMapper.readValue(responseString, ProcessDefinitionXmlEngineDto.class);
      response.close();
      return xml;
    } catch (IOException e) {
      String errorMessage =
        String.format("Could not fetch the process definition xml for id [%s]!", processDefinitionId);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  public DecisionDefinitionXmlEngineDto getDecisionDefinitionXml(String decisionDefinitionId) {
    HttpRequestBase get = new HttpGet(getDecisionDefinitionXmlUri(decisionDefinitionId));
    CloseableHttpResponse response;
    try {
      response = client.execute(get);
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      DecisionDefinitionXmlEngineDto xml =
        objectMapper.readValue(responseString, DecisionDefinitionXmlEngineDto.class);
      response.close();
      return xml;
    } catch (IOException e) {
      String errorMessage =
        String.format("Could not fetch the decision definition xml for id [%s]!", decisionDefinitionId);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  private List<DeploymentDto> deployProcess(BpmnModelInstance bpmnModelInstance, List<String> tenants) {
    String process = Bpmn.convertToString(bpmnModelInstance);
    List<HttpPost> deploymentRequest = createProcessDeploymentRequest(process, tenants);
    return deploymentRequest.stream().map(d -> {
      DeploymentDto deployment = new DeploymentDto();
      try (CloseableHttpResponse response = client.execute(d)) {
        String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
        if (response.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
          throw new RuntimeException("Something really bad happened during deployment, " +
                                       "could not create a deployment!\n" +
                                       responseString);
        }
        deployment = objectMapper.readValue(responseString, DeploymentDto.class);
      } catch (IOException e) {
        log.error("Error during deployment request! Could not deploy the given process model!", e);
      }
      return deployment;
    }).collect(Collectors.toList());
  }

  private List<HttpPost> createProcessDeploymentRequest(String process, List<String> tenants) {
    return tenants.stream().map(t -> {
      HttpPost post = new HttpPost(getCreateDeploymentUri());
      MultipartEntityBuilder builder = MultipartEntityBuilder
        .create()
        .addTextBody("deployment-name", "deployment")
        .addTextBody("enable-duplicate-filtering", "false")
        .addTextBody("deployment-source", "process application");

      if (t != null) {
        builder.addTextBody("tenant-id", t);
      }

      HttpEntity entity = builder.addBinaryBody(
        "data",
        process.getBytes(StandardCharsets.UTF_8),
        ContentType.APPLICATION_OCTET_STREAM,
        "some_process.bpmn"
      ).build();
      post.setEntity(entity);
      return post;
    }).collect(Collectors.toList());
  }

  private List<HttpPost> createDecisionDeploymentRequest(String decision, List<String> tenants) {
    return tenants.stream().map(t -> {
      HttpPost post = new HttpPost(getCreateDeploymentUri());
      MultipartEntityBuilder builder = MultipartEntityBuilder
        .create()
        .addTextBody("deployment-name", "deployment")
        .addTextBody("enable-duplicate-filtering", "false")
        .addTextBody("deployment-source", "process application");

      if (t != null) {
        builder.addTextBody("tenant-id", t);
      }

      HttpEntity entity = builder.addBinaryBody(
        "data",
        decision.getBytes(StandardCharsets.UTF_8),
        ContentType.APPLICATION_OCTET_STREAM,
        "decision.dmn"
      ).build();

      post.setEntity(entity);
      return post;
    }).collect(Collectors.toList());
  }

  @SneakyThrows
  public List<EngineIncidentDto> getFirst100Incidents() {
    HttpRequestBase get = new HttpGet(getIncidentUri());
    final URI uri;
    try {
      uri = new URIBuilder(get.getURI())
        .addParameter("maxSize", "100")
        // sort by incident ID to randomize the selection. Otherwise, we would get the latest
        // incidents first which would result in a very unequal distribution across processes.
        .addParameter("sortBy", "incidentId")
        .addParameter("sortOrder", "desc")
        .build();
    } catch (URISyntaxException e) {
      throw new OptimizeRuntimeException("Could not build uri!", e);
    }
    get.setURI(uri);
    try (CloseableHttpResponse response = client.execute(get)) {
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      return objectMapper.readValue(
        responseString,
        new TypeReference<List<EngineIncidentDto>>() {
        }
      );
    } catch (IOException e) {
      String message = "Could not retrieve incidents!";
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  @SneakyThrows
  public void addVariableToProcessInstance(final String processInstanceId, final String variableName,
                                           final VariableValueDto variableValueDto) {
    HttpPut put = new HttpPut(getAddVariableToProcessInstanceUri(processInstanceId, variableName));
    put.addHeader("Content-Type", "application/json");
    put.setEntity(new StringEntity(objectMapper.writeValueAsString(variableValueDto)));
    try (CloseableHttpResponse response = client.execute(put)) {
      if (response.getStatusLine().getStatusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
        throw new OptimizeRuntimeException(
          String.format(
            "Could not add variable [%s] to process instance with id [%s]. \n " +
              "Status-code: %s \n Error message: %s",
            variableName, processInstanceId,
            response.getStatusLine().getStatusCode(),
            EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8)
          )
        );
      }
    } catch (IOException e) {
      String message = String.format("Could not add variable to process instance with id [%s]", processInstanceId);
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private void createGrantAllOfTypeUserAuthorization(final int resourceType, final String userId) {
    createGrantAllOfTypeAuthorization(resourceType, userId, null);
  }

  private void createGrantAllOfTypeGroupAuthorization(final int resourceType, final String groupId) {
    createGrantAllOfTypeAuthorization(resourceType, null, groupId);
  }

  @SneakyThrows
  private void createGrantAllOfTypeAuthorization(final int resourceType, final String userId, final String groupId) {
    final HttpPost authPost = new HttpPost(engineRestEndpoint + "/authorization/create");
    final AuthorizationDto globalAppAuth = new AuthorizationDto(
      null, 1, Collections.singletonList("ALL"), userId, groupId, resourceType, "*"
    );
    authPost.setEntity(new StringEntity(objectMapper.writeValueAsString(globalAppAuth)));
    authPost.addHeader("Content-Type", "application/json");

    try (CloseableHttpResponse createUserResponse = client.execute(authPost)) {
      log.info(
        "Response Status Code {} when granting ALL authorization for resource type {} to user {}.",
        createUserResponse.getStatusLine().getStatusCode(),
        resourceType,
        userId
      );
      if (createUserResponse.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
        log.warn(readEntityAsString(createUserResponse));
      }
    }
  }

  public void deleteCandidateGroupForAllRunningUserTasks(final String groupId) {
    try (final CloseableHttpClient httpClient = createClientWithDefaultBasicAuth()) {
      final List<org.camunda.optimize.rest.engine.dto.TaskDto> tasks = getUserTasks(httpClient, null);
      for (org.camunda.optimize.rest.engine.dto.TaskDto task : tasks) {
        deleteCandidateGroupFromUserTask(httpClient, task.getId(), groupId);
      }
    } catch (IOException e) {
      log.error("Error while trying to create http client auth authentication!", e);
    }
  }

  private void deleteCandidateGroupFromUserTask(final CloseableHttpClient httpClient,
                                                final String taskId,
                                                final String groupId) throws IOException {
    HttpPost deleteCandidateGroupPost = new HttpPost(getDeleteIdentityLinkUrl(taskId));
    String bodyAsString = String.format("{\"groupId\": \"%s\", \"type\": \"candidate\"}", groupId);
    deleteCandidateGroupPost.setEntity(new StringEntity(bodyAsString));
    deleteCandidateGroupPost.addHeader("Content-Type", "application/json");
    try (CloseableHttpResponse response = httpClient.execute(deleteCandidateGroupPost)) {
      if (response.getStatusLine().getStatusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
        throw new OptimizeRuntimeException(
          "Could not delete candidate group! Status-code: " + response.getStatusLine().getStatusCode()
        );
      }
    }
  }

  public void finishAllRunningUserTasks(final String user, final String password, final String processInstanceId) {
    try (final CloseableHttpClient httpClient = createClientWithBasicAuth(user, password)) {
      final List<org.camunda.optimize.rest.engine.dto.TaskDto> tasks = getUserTasks(httpClient, processInstanceId);
      for (org.camunda.optimize.rest.engine.dto.TaskDto task : tasks) {
        claimAndCompleteUserTask(httpClient, user, task);
      }
    } catch (IOException e) {
      log.error("Error while trying to create http client auth authentication!", e);
    }
  }

  public void claimAllRunningUserTasks(final String user, final String password, final String processInstanceId) {
    claimAllRunningUserTasksWithAssignee(user, user, password, processInstanceId);
  }

  public void claimAllRunningUserTasksWithAssignee(final String assigneeId, final String user,
                                                   final String password, final String processInstanceId) {
    try (final CloseableHttpClient httpClient = createClientWithBasicAuth(user, password)) {
      final List<org.camunda.optimize.rest.engine.dto.TaskDto> tasks = getUserTasks(httpClient, processInstanceId);
      for (org.camunda.optimize.rest.engine.dto.TaskDto task : tasks) {
        claimUserTask(httpClient, assigneeId, task);
      }
    } catch (IOException e) {
      log.error("Error while trying to create http client auth authentication!", e);
    }
  }

  public void unclaimAllRunningUserTasks(final String user, final String password, final String processInstanceId) {
    try (final CloseableHttpClient httpClient = createClientWithBasicAuth(user, password)) {
      final List<org.camunda.optimize.rest.engine.dto.TaskDto> tasks = getUserTasks(httpClient, processInstanceId);
      for (org.camunda.optimize.rest.engine.dto.TaskDto task : tasks) {
        unclaimUserTask(httpClient, user, task);
      }
    } catch (IOException e) {
      log.error("Error while trying to create http client auth authentication!", e);
    }
  }

  private void unclaimUserTask(final CloseableHttpClient authenticatingClient, final String userId,
                               final org.camunda.optimize.rest.engine.dto.TaskDto task)
    throws IOException {
    HttpPost claimPost = new HttpPost(getSecuredUnclaimTaskUri(task.getId()));
    claimPost.setEntity(new StringEntity("{ \"userId\" : \"" + userId + "\" }"));
    claimPost.addHeader("Content-Type", "application/json");
    try (CloseableHttpResponse response = authenticatingClient.execute(claimPost)) {
      if (response.getStatusLine().getStatusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
        throw new OptimizeRuntimeException(
          "Could not unclaim user task! Status-code: " + response.getStatusLine().getStatusCode()
        );
      }
    }
  }

  private void claimAndCompleteUserTask(final CloseableHttpClient authenticatingClient, final String userId,
                                        final org.camunda.optimize.rest.engine.dto.TaskDto task)
    throws IOException {
    claimUserTask(authenticatingClient, userId, task);
    completeUserTask(authenticatingClient, task);
  }

  private void claimUserTask(final CloseableHttpClient authenticatingClient, final String userId,
                             final org.camunda.optimize.rest.engine.dto.TaskDto task)
    throws IOException {
    HttpPost claimPost = new HttpPost(getSecuredClaimTaskUri(task.getId()));
    claimPost.setEntity(new StringEntity("{ \"userId\" : \"" + userId + "\" }"));
    claimPost.addHeader("Content-Type", "application/json");
    try (CloseableHttpResponse response = authenticatingClient.execute(claimPost)) {
      if (response.getStatusLine().getStatusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
        throw new OptimizeRuntimeException(
          "Could not claim user task! Status-code: " + response.getStatusLine().getStatusCode()
        );
      }
    }
  }

  public String getProcessDefinitionId() {
    HttpRequestBase get = new HttpGet(getProcessDefinitionUri());
    CloseableHttpResponse response;
    try {
      response = client.execute(get);
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      List<ProcessDefinitionEngineDto> procDefs =
        objectMapper.readValue(responseString, new TypeReference<List<ProcessDefinitionEngineDto>>() {
        });
      response.close();
      assertOnlyOneDeployment(procDefs);
      return procDefs.get(0).getId();
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Could not fetch the process definition!", e);
    }
  }

  public ProcessInstanceEngineDto deployAndStartProcessWithVariables(BpmnModelInstance bpmnModelInstance,
                                                                     Map<String, Object> variables,
                                                                     String businessKey,
                                                                     String tenantId) {
    final DeploymentDto deployment = deployProcess(bpmnModelInstance, tenantId);
    final List<ProcessDefinitionEngineDto> procDefs = getAllProcessDefinitions(deployment, client);
    assertOnlyOneDeployment(procDefs);
    final ProcessDefinitionEngineDto processDefinitionEngineDto = procDefs.get(0);
    final ProcessInstanceEngineDto processInstanceDto = startProcessInstance(
      processDefinitionEngineDto.getId(), variables, businessKey
    );
    processInstanceDto.setProcessDefinitionKey(processDefinitionEngineDto.getKey());
    processInstanceDto.setProcessDefinitionVersion(String.valueOf(processDefinitionEngineDto.getVersion()));

    return processInstanceDto;
  }

  public HistoricProcessInstanceDto getHistoricProcessInstance(String processInstanceId) {
    HttpRequestBase get = new HttpGet(getHistoricGetProcessInstanceUri(processInstanceId));
    HistoricProcessInstanceDto processInstanceDto = new HistoricProcessInstanceDto();
    try {
      CloseableHttpResponse response = client.execute(get);
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      processInstanceDto = objectMapper.readValue(responseString, new TypeReference<HistoricProcessInstanceDto>() {
      });
      response.close();
    } catch (IOException e) {
      log.error("Could not get historic process instance for process instance ID: " + processInstanceId, e);
    }
    return processInstanceDto;
  }

  public List<HistoricActivityInstanceEngineDto> getHistoricActivityInstances() {
    HttpRequestBase get = new HttpGet(getHistoricGetActivityInstanceUri());
    try (CloseableHttpResponse response = client.execute(get)) {
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      return objectMapper.readValue(responseString, new TypeReference<List<HistoricActivityInstanceEngineDto>>() {
      });
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Could not fetch historic activity instances", e);
    }
  }

  @SneakyThrows
  public void cancelActivityInstance(final String processInstanceId, final String activityId) {
    final String activityInstanceId = getHistoricActivityInstances()
      .stream()
      .filter(inst -> inst.getProcessInstanceId().equalsIgnoreCase(processInstanceId)
        && inst.getActivityId().equalsIgnoreCase(activityId))
      .findFirst().orElseThrow(() -> new OptimizeRuntimeException("No Activity Instances found!"))
      .getId();
    HttpPost cancelRequest = new HttpPost(engineRestEndpoint + "/process-instance/" + processInstanceId +
                                            "/modification");
    cancelRequest.setHeader("Content-type", "application/json");
    cancelRequest.setEntity(new StringEntity(
      "{\n" +
        " \"skipCustomListeners\": true,\n" +
        " \"skipIoMappings\": true,\n" +
        " \"instructions\": [" +
        "    {" +
        "      \"type\": \"cancel\"," +
        "      \"activityInstanceId\": \"" + activityInstanceId + "\"" +
        "    }" +
        "  ]\n" +
        "}"
    ));
    try (CloseableHttpResponse response = client.execute(cancelRequest)) {
      if (response.getStatusLine().getStatusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
        throw new OptimizeRuntimeException(
          String.format(
            "Could not cancel activity instance with ID %s from process instance with ID %s. Status-code: %s",
            activityInstanceId, processInstanceId, response.getStatusLine().getStatusCode()
          )
        );
      }
    }
  }

  public void deleteHistoricProcessInstance(String processInstanceId) {
    HttpDelete delete = new HttpDelete(getHistoricGetProcessInstanceUri(processInstanceId));
    try (CloseableHttpResponse response = client.execute(delete)) {
      if (response.getStatusLine().getStatusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
        log.error(
          "Could not delete historic process instance for process instance ID [{}]. Reason: wrong response code [{}]",
          processInstanceId,
          response.getStatusLine().getStatusCode()
        );
      }
    } catch (Exception e) {
      log.error("Could not delete historic process instance for process instance ID: " + processInstanceId, e);
    }
  }

  public List<HistoricUserTaskInstanceDto> getHistoricTaskInstances(String processInstanceId,
                                                                    String taskDefinitionKey) {
    try {
      final URIBuilder historicGetUserTaskInstanceUriBuilder = new URIBuilder(getHistoricTaskInstanceUri())
        .addParameter("processInstanceId", processInstanceId);

      if (taskDefinitionKey != null) {
        historicGetUserTaskInstanceUriBuilder.addParameter("taskDefinitionKey", taskDefinitionKey);
      }

      final HttpRequestBase get = new HttpGet(historicGetUserTaskInstanceUriBuilder.build());
      try (final CloseableHttpResponse response = client.execute(get)) {
        String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
        // @formatter:off
        return objectMapper.readValue(
          responseString,
          new TypeReference<List<HistoricUserTaskInstanceDto>>() {}
        );
        // @formatter:on
      } catch (IOException e) {
        throw new OptimizeRuntimeException(
          String.format(
            "Could not get historic user task instances with taskDefinitionKey %s for process instance ID:%s",
            taskDefinitionKey,
            processInstanceId
          ),
          e
        );
      }
    } catch (URISyntaxException e) {
      throw new OptimizeRuntimeException("Failed building task instance url", e);
    }

  }

  public void deleteVariableInstanceForProcessInstance(String variableName, String processInstanceId) {
    HttpDelete delete = new HttpDelete(getVariableUri(variableName, processInstanceId));
    try (CloseableHttpResponse response = client.execute(delete)) {
      if (response.getStatusLine().getStatusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
        log.error(
          "Could not delete variable [{}] for process instance [{}]. Reason: wrong response code [{}]",
          variableName,
          processInstanceId,
          response.getStatusLine().getStatusCode()
        );
      }
    } catch (Exception e) {
      log.error("Could not delete variable for process instance: " + processInstanceId, e);
    }
  }

  @SneakyThrows
  public void updateVariableInstanceForProcessInstance(final String processInstanceId, final String variableName,
                                                       final String variableValue) {
    final HttpPut updateVar = new HttpPut(getVariableUri(variableName, processInstanceId));
    updateVar.setHeader("Content-type", MediaType.APPLICATION_JSON);
    updateVar.setEntity(new StringEntity(variableValue));
    try (CloseableHttpResponse response = client.execute(updateVar)) {
      if (response.getStatusLine().getStatusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
        log.error(
          "Could not update variable [{}] for process instance [{}]. Reason: wrong response code [{}]",
          variableName,
          processInstanceId,
          response.getStatusLine().getStatusCode()
        );
      }
    } catch (Exception e) {
      log.error("Could not update variable for process instance: " + processInstanceId, e);
    }
  }

  public DeploymentDto deployProcess(BpmnModelInstance bpmnModelInstance,
                                     String tenantId) {
    String process = Bpmn.convertToString(bpmnModelInstance);
    HttpPost deploymentRequest = createDeploymentRequest(process, "test.bpmn", tenantId);
    DeploymentDto deployment = new DeploymentDto();
    try (CloseableHttpResponse response = client.execute(deploymentRequest)) {
      if (response.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
        throw new OptimizeRuntimeException("Something really bad happened during deployment, " +
                                             "could not create a deployment!");
      }
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      deployment = objectMapper.readValue(responseString, DeploymentDto.class);
      response.close();
    } catch (IOException e) {
      log.error("Error during deployment request! Could not deploy the given process model!", e);
    }
    return deployment;
  }

  public void performProcessDefinitionSuspensionByIdRequest(
    final String processDefinitionId,
    final boolean suspended) throws IOException {
    performSuspensionRequest(
      getSuspendProcessDefinitionByIdUri(processDefinitionId),
      new StringEntity(
        "{\n" +
          "\"includeProcessInstances\": true,\n" +
          "\"suspended\": " + suspended + "\n" +
          "}"
      )
    );
  }

  public void performProcessDefinitionSuspensionByKeyRequest(
    final String processDefinitionKey,
    final boolean suspended) throws IOException {
    performSuspensionRequest(
      getSuspendProcessDefinitionByKeyUri(),
      new StringEntity(
        "{\n" +
          "\"processDefinitionKey\": \"" + processDefinitionKey + "\",\n" +
          "\"includeProcessInstances\": true,\n" +
          "\"suspended\": " + suspended + "\n" +
          "}"
      )
    );
  }

  public void performProcessInstanceByInstanceIdSuspensionRequest(
    final String processInstanceId,
    final boolean suspended) throws IOException {
    performSuspensionRequest(
      getSuspendProcessInstanceByInstanceIdUri(processInstanceId),
      new StringEntity(
        "{\n" +
          "\"suspended\": " + suspended + "\n" +
          "}"
      )
    );
  }

  public void performProcessInstanceByDefinitionIdSuspensionRequest(
    final String processDefinitionId,
    final boolean suspended) throws IOException {
    performSuspensionRequest(
      getSuspendProcessInstanceByDefinitionUri(),
      new StringEntity(
        "{\n" +
          "\"processDefinitionId\": \"" + processDefinitionId + "\",\n" +
          "\"suspended\": " + suspended + "\n" +
          "}"
      )
    );
  }

  public void performProcessInstanceByDefinitionKeySuspensionRequest(
    final String processDefinitionKey,
    final boolean suspended) throws IOException {
    performSuspensionRequest(
      getSuspendProcessInstanceByDefinitionUri(),
      new StringEntity(
        "{\n" +
          "\"processDefinitionKey\": \"" + processDefinitionKey + "\",\n" +
          "\"suspended\": \"" + suspended + "\"\n" +
          "}"
      )
    );
  }

  public void performProcessInstanceSuspensionViaBatchRequestAndForceBatchExecution(
    final String processInstanceId,
    final boolean suspended) throws IOException {
    HttpPost suspendRequest = new HttpPost(getSuspendProcessInstanceViaBatchUri());
    suspendRequest.setHeader("Content-type", "application/json");
    suspendRequest.setEntity(new StringEntity(
      "{\n" +
        "\"processInstanceIds\": [\"" + processInstanceId + "\"],\n" +
        "\"suspended\": \"" + suspended + "\"\n" +
        "}"
    ));
    try (CloseableHttpResponse response = client.execute(suspendRequest)) {
      if (response.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
        throw new OptimizeRuntimeException(
          String.format(
            "Could not suspend or activate process instance with ID %s via batch. Status-code: %s",
            processInstanceId,
            response.getStatusLine().getStatusCode()
          )
        );
      }
      final String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      final JSONObject batchJsonObject = (JSONObject) JSONValue.parse(responseString);
      executeBatch(batchJsonObject);
    }
  }

  private void performSuspensionRequest(final String suspensionUri,
                                        final StringEntity entity) throws IOException {
    HttpPut suspendRequest = new HttpPut(suspensionUri);
    suspendRequest.setHeader("Content-type", "application/json");
    suspendRequest.setEntity(entity);
    try (CloseableHttpResponse response = client.execute(suspendRequest)) {
      if (response.getStatusLine().getStatusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
        throw new OptimizeRuntimeException(
          String.format(
            "Could not execute suspend operation on endpoint [%s] with parameters [%s]. Status-code: %s",
            suspensionUri,
            entity,
            response.getStatusLine().getStatusCode()
          )
        );
      }
    }
  }

  public void startDecisionInstance(String decisionDefinitionId,
                                    Map<String, Object> variables) {
    final HttpPost post = new HttpPost(getStartDecisionInstanceUri(decisionDefinitionId));
    post.addHeader("Content-Type", "application/json");
    final Map<String, Object> requestBodyAsMap = convertVariableMap(variables);

    final String requestBodyAsJson;
    try {
      requestBodyAsJson = objectMapper.writeValueAsString(requestBodyAsMap);
      post.setEntity(new StringEntity(requestBodyAsJson, ContentType.APPLICATION_JSON));
      try (final CloseableHttpResponse response = client.execute(post)) {
        if (response.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
          String body = "";
          if (response.getEntity() != null) {
            body = EntityUtils.toString(response.getEntity());
          }
          throw new OptimizeRuntimeException(
            "Could not start the decision definition instance. " +
              "Request: [" + post.toString() + "]. " +
              "Response: [" + body + "]"
          );
        }
      }
    } catch (IOException e) {
      final String message = "Could not start the given decision model!";
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private void executeBatch(final JSONObject batchJsonObject) throws IOException {
    // First execute the seed job
    final String seedJobDefinitionId = batchJsonObject.getAsString("seedJobDefinitionId");
    final String seedJobId = getJobId(seedJobDefinitionId);
    executeJob(seedJobId);

    // Then execute the batch job
    final String batchJobDefinitionId = batchJsonObject.getAsString("batchJobDefinitionId");
    final String batchJobId = getJobId(batchJobDefinitionId);
    executeJob(batchJobId);
  }

  private String getJobId(final String jobDefinitionId) throws IOException {
    HttpPost getJobRequest = new HttpPost(getGetJobUri());
    getJobRequest.setHeader("Content-type", "application/json");
    getJobRequest.setEntity(new StringEntity(
      "{\n" +
        "\"jobDefinitionId\": \"" + jobDefinitionId + "\"\n" +
        "}"
    ));
    try (CloseableHttpResponse response = client.execute(getJobRequest)) {
      if (response.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
        throw new OptimizeRuntimeException(
          String.format(
            "Could not get job with jobDefinitionID %s. Status-code: %s",
            jobDefinitionId,
            response.getStatusLine().getStatusCode()
          )
        );
      }
      final String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");

      final JSONArray responseJsonArray = (JSONArray) JSONValue.parse(responseString);
      if (responseJsonArray.size() != 1) {
        throw new OptimizeRuntimeException(
          String.format(
            "Could not find unique job with jobDefinitionID %s. Found: %s",
            jobDefinitionId,
            responseJsonArray
          )
        );
      }
      final JSONObject jobJson = (JSONObject) responseJsonArray.get(0);
      return jobJson.getAsString("id");
    }
  }

  @SneakyThrows
  private void executeJob(final String jobId) {
    HttpPost executeJobRequest = new HttpPost(getExecuteJobUri(jobId));
    try (CloseableHttpResponse response = client.execute(executeJobRequest)) {
      if (response.getStatusLine().getStatusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
        throw new OptimizeRuntimeException(
          String.format(
            "Could not execute job with jobID %s. Status-code: %s",
            jobId,
            response.getStatusLine().getStatusCode()
          )
        );
      }
      log.debug("Executed Job with ID " + jobId);
    }
  }

  private HttpPost createDeploymentRequest(String process, String fileName, String tenantId) {
    HttpPost post = new HttpPost(getCreateDeploymentUri());
    final MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder
      .create()
      .addTextBody("deployment-name", "deployment")
      .addTextBody("enable-duplicate-filtering", "false")
      .addTextBody("deployment-source", "process application")
      .addBinaryBody(
        "data",
        process.getBytes(StandardCharsets.UTF_8),
        ContentType.APPLICATION_OCTET_STREAM,
        fileName
      );

    if (tenantId != null) {
      multipartEntityBuilder.addTextBody("tenant-id", tenantId);
    }

    final HttpEntity entity = multipartEntityBuilder.build();
    post.setEntity(entity);
    return post;
  }

  public ProcessDefinitionEngineDto getProcessDefinitionEngineDto(final DeploymentDto deployment) {
    final List<ProcessDefinitionEngineDto> processDefinitions = getAllProcessDefinitions(
      deployment, client
    );
    assertOnlyOneDeployment(processDefinitions);
    return processDefinitions.get(0);
  }

  private List<ProcessDefinitionEngineDto> getAllProcessDefinitions(DeploymentDto deployment,
                                                                    CloseableHttpClient client) {
    HttpRequestBase get = new HttpGet(getProcessDefinitionUri());
    URI uri = null;
    try {
      uri = new URIBuilder(get.getURI())
        .addParameter("deploymentId", deployment.getId())
        .build();
    } catch (URISyntaxException e) {
      log.error("Could not build uri!", e);
    }
    get.setURI(uri);
    CloseableHttpResponse response = null;
    try {
      response = client.execute(get);
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      return objectMapper.readValue(
        responseString,
        new TypeReference<List<ProcessDefinitionEngineDto>>() {
        }
      );
    } catch (IOException e) {
      String message = "Could not retrieve all process definitions!";
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    } finally {
      if (response != null) {
        try {
          response.close();
        } catch (IOException e) {
          String message = "Could not close response!";
          log.error(message, e);
        }
      }
    }
  }

  @SneakyThrows
  public void failExternalTasks(final String businessKey) {
    final List<ExternalTaskEngineDto> externalTasks = fetchAndLockExternalTasksWithBusinessKey(businessKey);
    for (ExternalTaskEngineDto externalTask : externalTasks) {
      HttpPost executeJobRequest = new HttpPost(getExternalTaskFailureUri(externalTask.getId()));
      executeJobRequest.addHeader("Content-Type", "application/json");
      executeJobRequest.setEntity(new StringEntity(
        String.format(
          "{\n" +
            "\"workerId\": \"" + "%s" + "\",\n" +
            "\"errorMessage\": \"" + "Should fail on purpose!" + "\",\n" +
            "\"retries\": " + 0 + ",\n" +
            "\"retryTimeout\": " + 10000 + "\n" +
            "}",
          DEFAULT_WORKER
        )
      ));
      try (CloseableHttpResponse response = client.execute(executeJobRequest)) {
        if (response.getStatusLine().getStatusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
          throw new OptimizeRuntimeException(
            String.format(
              "Could not fail external task with id %s. Status-code: %s",
              externalTask.getId(),
              response.getStatusLine().getStatusCode()
            )
          );
        }
        log.debug("Failed external task with ID " + externalTask.getId());
      }
    }
  }

  @SneakyThrows
  public void increaseJobRetry(final List<String> processInstanceIds) {
    HttpPost httpPost = new HttpPost(getJobRetriesUri());
    httpPost.addHeader("Content-Type", "application/json");
    String commaSeparatedIds = processInstanceIds.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
    // @formatter:off
    httpPost.setEntity(new StringEntity(
      "{\n" +
        "\"retries\": " + 1 + ",\n" +
        "\"jobQuery\": {" +
            "\"processInstanceIds\": [" + commaSeparatedIds + "] \n" +
          "}" +
        "}"
    ));
    // @formatter:on
    try (CloseableHttpResponse response = client.execute(httpPost)) {
      if (response.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
        throw new OptimizeRuntimeException(
          String.format(
            "Could not increase retries for process instances with ids [%s]. \n " +
              "Status-code: %s \n Error message: %s",
            commaSeparatedIds,
            response.getStatusLine().getStatusCode(),
            EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8)
          )
        );
      }
    } catch (IOException e) {
      String message = "Could not increment retry of process instances!";
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  @SneakyThrows
  public List<ExternalTaskEngineDto> fetchAndLockExternalTasksWithBusinessKey(final String businessKey) {
    HttpPost post = new HttpPost(getExternalTaskFetchAndLockUri());
    post.addHeader("Content-Type", "application/json");
    post.setEntity(createFetchAndLockEntity(businessKey));
    try (CloseableHttpResponse response = client.execute(post)) {
      if (response.getStatusLine().getStatusCode() == Response.Status.OK.getStatusCode()) {
        String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
        return objectMapper.readValue(responseString, new TypeReference<List<ExternalTaskEngineDto>>() {
        });
      } else {
        throw new OptimizeRuntimeException(
          String.format(
            "Could not fetch and lock external tasks. Status-code: %s",
            response.getStatusLine().getStatusCode()
          )
        );
      }
    } catch (IOException e) {
      String message = "Could not fetch and lock external tasks!";
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  @SneakyThrows
  public void completeExternalTask(final ExternalTaskEngineDto externalTaskEngineDto) {
    HttpPost post = new HttpPost(getExternalTaskCompleteUri(externalTaskEngineDto.getId()));
    post.addHeader("Content-Type", "application/json");
    post.setEntity(new StringEntity(
      String.format(
        "{\n" +
          "\"workerId\": \"" + "%s" + "\"\n" +
          "}",
        DEFAULT_WORKER
      )
    ));
    try (CloseableHttpResponse response = client.execute(post)) {
      if (response.getStatusLine().getStatusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
        throw new OptimizeRuntimeException(
          String.format(
            "Could not complete external task with id [%s]. Status-code: %s",
            externalTaskEngineDto.getId(),
            response.getStatusLine().getStatusCode()
          )
        );
      }
    } catch (IOException e) {
      String message = "Could not complete external task!";
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  @SneakyThrows
  public void increaseRetry(final List<ExternalTaskEngineDto> externalTasks) {
    HttpPut put = new HttpPut(getExternalTaskRetriesUri());
    put.addHeader("Content-Type", "application/json");
    String commaSeparatedTaskIds = externalTasks.stream()
      .map(ExternalTaskEngineDto::getId)
      .map(id -> "\"" + id + "\"")
      .collect(Collectors.joining(","));
    // @formatter:off
    put.setEntity(new StringEntity(
      "{\n" +
        "\"retries\": \"" + 1 + "\",\n" +
        "\"externalTaskIds\": [" + commaSeparatedTaskIds + "]\n" +
      "}"
    // @formatter:on
    ));
    try (CloseableHttpResponse response = client.execute(put)) {
      if (response.getStatusLine().getStatusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
        throw new OptimizeRuntimeException(
          String.format(
            "Could not set retries for external tasks with ids [%s]. Status-code: %s",
            commaSeparatedTaskIds,
            response.getStatusLine().getStatusCode()
          )
        );
      }
    } catch (IOException e) {
      String message = "Could not adjust retry of external tasks!";
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  @SneakyThrows
  public List<ExternalTaskEngineDto> getExternalTasks(final String processInstanceId) {
    HttpRequestBase get = new HttpGet(getExternalTaskUri());
    if (processInstanceId != null) {
      URI uri = null;
      try {
        uri = new URIBuilder(get.getURI())
          .addParameter("processInstanceId", processInstanceId)
          .build();
      } catch (URISyntaxException e) {
        log.error("Could not build uri!", e);
      }
      get.setURI(uri);
    }
    try (CloseableHttpResponse response = client.execute(get)) {
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      return objectMapper.readValue(
        responseString,
        new TypeReference<List<ExternalTaskEngineDto>>() {
        }
      );
    } catch (IOException e) {
      String message = "Could not retrieve all external tasks!";
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  @SneakyThrows
  public List<HistoricIncidentEngineDto> getHistoricIncidents() {
    HttpRequestBase get = new HttpGet(getHistoricIncidentUri());
    try (CloseableHttpResponse response = client.execute(get)) {
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      return objectMapper.readValue(
        responseString,
        new TypeReference<List<HistoricIncidentEngineDto>>() {
        }
      );
    } catch (IOException e) {
      String message = "Could not retrieve historic incidents!";
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  @SneakyThrows
  public void createIncident(final String processInstanceId, final String customIncidentType) {
    final List<ExecutionDto> executions = getExecutionsForProcessInstance(processInstanceId);
    if (executions.size() == 1) {
      // if it's just a single execution then execution == process instance
      createIncidentForExecutionId(processInstanceId, customIncidentType);
    } else {
      // if there are external tasks or there's some other kind of scope in the process
      // the process instance execution is not associated with an activity and, hence,
      // we need to filter for the newly created execution that is.
      executions.stream()
        .filter(execution -> !execution.getId().equals(processInstanceId))
        .forEach(
          execution -> createIncidentForExecutionId(execution.getId(), customIncidentType)
        );
    }
  }

  @SneakyThrows
  private List<ExecutionDto> getExecutionsForProcessInstance(final String processInstanceId) {
    HttpRequestBase get = new HttpGet(getExecutionsForProcessInstanceUri(processInstanceId));
    try (CloseableHttpResponse response = client.execute(get)) {
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      return objectMapper.readValue(
        responseString,
        new TypeReference<List<ExecutionDto>>() {
        }
      );
    } catch (IOException e) {
      String message = "Could not retrieve executions for process instance!";
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  @SneakyThrows
  private void createIncidentForExecutionId(final String executionId, final String customIncidentType) {
    HttpPost post = new HttpPost(getIncidentCreationUri(executionId));
    post.addHeader("Content-Type", "application/json");
    post.setEntity(new StringEntity(
      // @formatter:off
      String.format(
        "{\n" +
          "\"incidentType\": \"" + "%s" + "\",\n" +
          "\"configuration\": \"Some configuration\",\n" +
          "\"message\": \"This incident is raised on purpose!\"\n" +
        "}",
        customIncidentType
      )
      // @formatter:on
    ));
    try (CloseableHttpResponse response = client.execute(post)) {
      if (response.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
        throw new OptimizeRuntimeException(
          String.format(
            "Could not create incident for execution with ID [%s]. \n Status-code: %s \n Response: %s",
            executionId,
            response.getStatusLine().getStatusCode(),
            EntityUtils.toString(response.getEntity(), "UTF-8")
          )
        );
      }
    } catch (IOException e) {
      String message = "Could not create incident for execution!";
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  @SneakyThrows
  public void deleteProcessInstance(final String processInstanceId) {
    HttpDelete delete = new HttpDelete(getGetProcessInstanceUri(processInstanceId));
    try (CloseableHttpResponse response = client.execute(delete)) {
      if (response.getStatusLine().getStatusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
        throw new OptimizeRuntimeException(
          String.format(
            "Could not delete process instance with id [%s]. Status-code: %s",
            processInstanceId,
            response.getStatusLine().getStatusCode()
          )
        );
      }
    } catch (IOException e) {
      String message = "Could not delete process instance!";
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  public ProcessInstanceEngineDto startProcessInstance(String procDefId,
                                                       Map<String, Object> variables,
                                                       String businessKey) {
    HttpPost post = new HttpPost(getStartProcessInstanceUri(procDefId));
    post.addHeader("Content-Type", "application/json");
    Map<String, Object> requestBodyAsMap = convertVariableMap(variables);
    requestBodyAsMap.put("businessKey", businessKey);
    String requestBodyAsJson;
    try {
      requestBodyAsJson = objectMapper.writeValueAsString(requestBodyAsMap);
      post.setEntity(new StringEntity(requestBodyAsJson, ContentType.APPLICATION_JSON));
      try (CloseableHttpResponse response = client.execute(post)) {
        if (response.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
          String body = "";
          if (response.getEntity() != null) {
            body = EntityUtils.toString(response.getEntity());
          }
          throw new OptimizeRuntimeException(
            "Could not start the process instance. " +
              "Request: [" + post.toString() + "]. " +
              "Response: [" + body + "]"
          );
        }
        String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
        return objectMapper.readValue(responseString, ProcessInstanceEngineDto.class);
      }
    } catch (IOException e) {
      String message = "Could not start the given process model!";
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  private Map<String, Object> convertVariableMap(Map<String, Object> plainVariables) {
    Map<String, Object> variables = createConvertedVariableMap(plainVariables);
    Map<String, Object> variableWrapper = new HashMap<>();
    variableWrapper.put("variables", variables);
    return variableWrapper;
  }

  private Map<String, Object> createConvertedVariableMap(final Map<String, Object> plainVariables) {
    Map<String, Object> variables = new HashMap<>();
    for (Map.Entry<String, Object> nameToValue : plainVariables.entrySet()) {
      Object value = nameToValue.getValue();
      if (value instanceof EngineVariableValue) {
        final EngineVariableValue typedVariable = (EngineVariableValue) value;
        Map<String, Object> fields = new HashMap<>();
        fields.put("value", typedVariable.getValue());
        fields.put("type", typedVariable.getType());
        variables.put(nameToValue.getKey(), fields);
      } else if (value instanceof VariableDto) {
        variables.put(nameToValue.getKey(), value);
      } else {
        Map<String, Object> fields = new HashMap<>();
        fields.put("value", nameToValue.getValue());
        fields.put("type", getSimpleName(nameToValue));
        variables.put(nameToValue.getKey(), fields);
      }
    }
    return variables;
  }

  private String getSimpleName(Map.Entry<String, Object> nameToValue) {
    String simpleName;
    if (nameToValue.getValue() == null) {
      simpleName = "null";
    } else {
      simpleName = nameToValue.getValue().getClass().getSimpleName();
      if (nameToValue.getValue().getClass().equals(OffsetDateTime.class)) {
        simpleName = Date.class.getSimpleName();
      }
    }
    return simpleName;
  }

  @SneakyThrows
  public void deleteProcessDefinition(final String definitionId) {
    HttpDelete delete = new HttpDelete(getProcessDefinitionUri() + "/" + definitionId);
    try (CloseableHttpResponse response = client.execute(delete)) {
      if (response.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
        throw new OptimizeRuntimeException(
          String.format(
            "Could not delete process definition with id [%s]. Status-code: %s",
            definitionId,
            response.getStatusLine().getStatusCode()
          )
        );
      }
    } catch (IOException e) {
      String message = "Could not delete process definition!";
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  @SneakyThrows
  public void deleteDeploymentById(final String deploymentId) {
    HttpDelete delete = new HttpDelete(getDeploymentUri() + deploymentId);
    try (CloseableHttpResponse response = client.execute(delete)) {
      if (response.getStatusLine().getStatusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
        throw new OptimizeRuntimeException(
          String.format(
            "Could not delete deployment with id [%s]. Status-code: %s",
            deploymentId,
            response.getStatusLine().getStatusCode()
          )
        );
      }
    } catch (IOException e) {
      String message = "Could not delete deployment!";
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  @SneakyThrows
  public DecisionDefinitionEngineDto getDecisionDefinitionByDeployment(DeploymentDto deployment) {
    HttpRequestBase get = new HttpGet(getDecisionDefinitionUri());
    URI uri = new URIBuilder(get.getURI()).addParameter("deploymentId", deployment.getId()).build();
    get.setURI(uri);
    try (CloseableHttpResponse response = client.execute(get)) {
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      final List<DecisionDefinitionEngineDto> decisionDefinitionEngineDtos = objectMapper.readValue(
        responseString, new TypeReference<List<DecisionDefinitionEngineDto>>() {
        }
      );
      return decisionDefinitionEngineDtos.get(0);
    }
  }

  @SneakyThrows
  public void waitForAllProcessesToFinish() {
    final HttpRequestBase get = new HttpGet(
      new URIBuilder(getCountHistoryUri()).addParameter("unfinished", "true").build()
    );
    await()
      .atMost(1, TimeUnit.SECONDS)
      .pollInterval(100, TimeUnit.MILLISECONDS)
      .until(() -> {
        try (CloseableHttpResponse response = client.execute(get)) {
          final JsonNode jsonNode = objectMapper.readTree(response.getEntity().getContent());
          return jsonNode.get(COUNT).asInt() == 0;
        }
      });
  }

  public void createTenant(final String id) {
    createTenant(id, id);
  }

  public void createTenant(final String id, final String name) {
    final TenantEngineDto tenantDto = new TenantEngineDto();
    tenantDto.setId(id);
    tenantDto.setName(name);

    try {
      HttpPost httpPost = new HttpPost(engineRestEndpoint + "/tenant/create");
      httpPost.addHeader("Content-Type", "application/json");
      httpPost.setEntity(new StringEntity(objectMapper.writeValueAsString(tenantDto), ContentType.APPLICATION_JSON));
      try (CloseableHttpResponse response = client.execute(httpPost)) {
        final int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != Response.Status.NO_CONTENT.getStatusCode()) {
          if (statusCode == Response.Status.BAD_REQUEST.getStatusCode()
            && readEntityAsString(response).contains("already exists")) {
            log.warn("Could not create tenant as it already exists");
          } else {
            throw new OptimizeRuntimeException("Could not create group! Status-code: " + statusCode);
          }
        }
      }
    } catch (Exception e) {
      throw new OptimizeRuntimeException("error creating tenant", e);
    }
  }

  public void updateTenant(final String id, final String name) {
    final TenantEngineDto tenantDto = new TenantEngineDto();
    tenantDto.setId(id);
    tenantDto.setName(name);

    try {
      HttpPut httpPut = new HttpPut(engineRestEndpoint + "/tenant/" + id);
      httpPut.addHeader("Content-Type", "application/json");
      httpPut.setEntity(new StringEntity(objectMapper.writeValueAsString(tenantDto), ContentType.APPLICATION_JSON));
      try (CloseableHttpResponse response = client.execute(httpPut)) {
        if (response.getStatusLine().getStatusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
          throw new OptimizeRuntimeException("Wrong status code when trying to update tenant!");
        }
      }
    } catch (Exception e) {
      throw new OptimizeRuntimeException("error updating tenant", e);
    }
  }

  @SneakyThrows
  public void unlockUser(String username) {
    final HttpUriRequest request = new HttpPost(engineRestEndpoint + "/user/" + username + "/unlock");
    request.addHeader("Content-Type", "application/json");
    try (final CloseableHttpResponse response = client.execute(request)) {
      if (response.getStatusLine().getStatusCode() != Response.Status.NO_CONTENT.getStatusCode()) {
        throw new OptimizeRuntimeException("Wrong status code when trying to unlock user!");
      }
    }
  }

  public void createAuthorization(AuthorizationDto authorizationDto) {
    try {
      HttpPost httpPost = new HttpPost(engineRestEndpoint + "/authorization/create");
      httpPost.addHeader("Content-Type", "application/json");

      httpPost.setEntity(
        new StringEntity(objectMapper.writeValueAsString(authorizationDto), ContentType.APPLICATION_JSON)
      );
      CloseableHttpResponse response = client.execute(httpPost);
      if (response.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
        throw new OptimizeRuntimeException(
          "Could not create authorization! Status-code: " + response.getStatusLine().getStatusCode()
        );
      }
      response.close();
    } catch (IOException e) {
      log.error("Could not create authorization", e);
    }
  }


  public void grantAllAuthorizations(String username) {
    for (int i = 0; i < 15; i++) {
      HashMap<String, Object> values = new HashMap<>();
      values.put("type", 1);
      values.put("permissions", new String[]{"ALL"});
      values.put("userId", username);
      values.put("groupId", null);
      values.put("resourceType", i);
      values.put("resourceId", "*");

      try {

        HttpPost httpPost = new HttpPost(engineRestEndpoint + "/authorization/create");
        httpPost.addHeader("Content-Type", "application/json");

        httpPost.setEntity(new StringEntity(objectMapper.writeValueAsString(values), ContentType.APPLICATION_JSON));
        CloseableHttpResponse response = client.execute(httpPost);
        if (response.getStatusLine().getStatusCode() != Response.Status.OK.getStatusCode()) {
          throw new OptimizeRuntimeException(
            "Could not create frant all authorization! Status-code: " + response.getStatusLine().getStatusCode()
          );
        }
        response.close();
      } catch (Exception e) {
        log.error("error creating authorization", e);
      }
    }

  }

  public void createGroup(final String id, final String name) {
    createGroup(new GroupDto(id, name, "anyGroupType"));
  }

  @SneakyThrows
  public void createGroup(final GroupDto groupDto) {
    HttpPost httpPost = new HttpPost(engineRestEndpoint + "/group/create");
    httpPost.addHeader("Content-Type", "application/json");
    httpPost.setEntity(new StringEntity(objectMapper.writeValueAsString(groupDto), ContentType.APPLICATION_JSON));
    try (final CloseableHttpResponse response = client.execute(httpPost)) {
      final int statusCode = response.getStatusLine().getStatusCode();
      if (statusCode != Response.Status.NO_CONTENT.getStatusCode()) {
        if (statusCode == Response.Status.BAD_REQUEST.getStatusCode()
          && readEntityAsString(response).contains("already exists")) {
          log.warn("Could not create group as it already exists");
        } else {
          throw new OptimizeRuntimeException("Could not create group! Status-code: " + statusCode);
        }
      }
    } catch (Exception e) {
      log.error("error creating group", e);
    }
  }

  private String readEntityAsString(final CloseableHttpResponse response) throws IOException {
    return IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
  }

  public void addUserToGroup(String userId, String groupId) {
    HttpPut put = new HttpPut(engineRestEndpoint + "/group/" + groupId + "/members/" + userId);
    put.addHeader("Content-Type", "application/json");
    put.setEntity(new StringEntity("", ContentType.APPLICATION_JSON));
    try (final CloseableHttpResponse response = client.execute(put)) {
      final int statusCode = response.getStatusLine().getStatusCode();
      if (statusCode != Response.Status.NO_CONTENT.getStatusCode()) {
        throw new OptimizeRuntimeException("Could not add user to group! Status-code: " + statusCode);
      }
    } catch (Exception e) {
      log.error("error creating group members", e);
    }
  }

  public AuthorizationDto createOptimizeApplicationAuthorizationDto() {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_APPLICATION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(OPTIMIZE_APPLICATION_RESOURCE_ID);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    return authorizationDto;
  }

  @SneakyThrows
  private StringEntity createFetchAndLockEntity(final String businessKey) {
    final String businessKeyPart = businessKey == null
      ? ""
      : String.format(",\n\"businessKey\": \"%s\"\n", businessKey);
    return new StringEntity(
      // @formatter:off
      String.format(
        "{\n" +
          "\"workerId\": \"" + "%s" + "\",\n" +
          "\"maxTasks\": " + 100 + ",\n" +
          "\"topics\": [{" +
          "\"topicName\": \"%s\", \n" +
            "\"lockDuration\": 1000000" +
              businessKeyPart +
          "}]\n" +
        "}",
        DEFAULT_WORKER,
        DEFAULT_TOPIC
      )
      // @formatter:on
    );
  }

  private void assertOnlyOneDeployment(final List<ProcessDefinitionEngineDto> procDefs) {
    if (procDefs.size() != 1) {
      throw new IllegalStateException("Deployment should contain only one process definition!");
    }
  }

  private String getIncidentUri() {
    return engineRestEndpoint + "/incident";
  }

  private String getJobRetriesUri() {
    return engineRestEndpoint + "/job/retries";
  }

  private String getStartDecisionInstanceUri(final String decisionDefinitionId) {
    return engineRestEndpoint + "/decision-definition/" + decisionDefinitionId + "/evaluate";
  }

  private String getSuspendProcessInstanceUri(final String processInstanceId) {
    return getBaseProcessInstanceUri() + "/" + processInstanceId + "/suspended";
  }

  private String getBaseProcessInstanceUri() {
    return engineRestEndpoint + "/process-instance";
  }

  private String getAddVariableToProcessInstanceUri(final String processInstanceId,
                                                    final String variableName) {
    return getBaseProcessInstanceUri() + "/" + processInstanceId + "/variables/" + variableName;
  }

  private String getTaskListCreatedAfterUri(final String processDefinitionId, long limit,
                                            final OffsetDateTime createdAfter) {
    return engineRestEndpoint + "/task?active=true&sortBy=created&sortOrder=asc" +
      "&processDefinitionId=" + processDefinitionId +
      "&maxResults=" + limit +
      "&createdAfter=" + serializeDateTimeToUrlEncodedString(createdAfter);
  }

  private String getTaskListCreatedOnUri(final String processDefinitionId, final OffsetDateTime createdOn) {
    return engineRestEndpoint + "/task?active=true" +
      "&processDefinitionId=" + processDefinitionId +
      "&createdOn=" + serializeDateTimeToUrlEncodedString(createdOn);
  }

  private String getProcessDefinitionUri() {
    return engineRestEndpoint + "/process-definition";
  }

  private String getDecisionDefinitionUri() {
    return engineRestEndpoint + "/decision-definition";
  }

  private String getTaskAssigneeUri(String taskId) {
    return engineRestEndpoint + "/task/" + taskId + "/assignee";
  }

  private String getCompleteTaskUri(String taskId) {
    return engineRestEndpoint + "/task/" + taskId + "/complete";
  }

  private String getTaskListUri() {
    return engineRestEndpoint + "/task";
  }

  private String getProcessDefinitionXmlUri(String processDefinitionId) {
    return getProcessDefinitionUri() + "/" + processDefinitionId + "/xml";
  }

  private String getDecisionDefinitionXmlUri(String decisionDefinitionId) {
    return getDecisionDefinitionUri() + "/" + decisionDefinitionId + "/xml";
  }

  private String getSecuredEngineUrl() {
    return engineRestEndpoint.replace("/engine-rest", "/engine-it-plugin/basic-auth");
  }

  private String getTaskIdentityLinksUri(String taskId) {
    return engineRestEndpoint + "/task/" + taskId + "/identity-links";
  }

  private String getDeploymentUri() {
    return engineRestEndpoint + "/deployment/";
  }

  private String getCreateDeploymentUri() {
    return engineRestEndpoint + "/deployment/create";
  }

  private String getStartProcessInstanceUri(String procDefId) {
    return engineRestEndpoint + "/process-definition/" + procDefId + "/start";
  }

  private String getHistoricIncidentUri() {
    return engineRestEndpoint + "/history/incident";
  }

  private String getIncidentCreationUri(final String processInstanceId) {
    return engineRestEndpoint + "/execution/" + processInstanceId + "/create-incident";
  }

  private String getExecutionsForProcessInstanceUri(final String processInstanceId) {
    return engineRestEndpoint + "/execution?processInstanceId=" + processInstanceId;
  }

  private String getHistoricGetProcessInstanceUri(String processInstanceId) {
    return engineRestEndpoint + "/history/process-instance/" + processInstanceId;
  }

  private String getHistoricGetActivityInstanceUri() {
    return engineRestEndpoint + "/history/activity-instance/";
  }

  private String getHistoricTaskInstanceUri() {
    return engineRestEndpoint + "/history/task";
  }

  private String getGetProcessInstanceUri(String processInstanceId) {
    return engineRestEndpoint + "/process-instance/" + processInstanceId;
  }

  private String getVariableUri(String variableName, String processInstanceId) {
    return engineRestEndpoint + "/process-instance/" + processInstanceId + "/variables/" + variableName;
  }

  private String getExternalTaskFetchAndLockUri() {
    return getExternalTaskUri() + "fetchAndLock";
  }

  private String getExternalTaskFailureUri(final String externalTaskId) {
    return getExternalTaskUri() + externalTaskId + "/failure";
  }

  private String getExternalTaskCompleteUri(final String externalTaskId) {
    return engineRestEndpoint + "/external-task/" + externalTaskId + "/complete";
  }

  private String getExternalTaskUri() {
    return engineRestEndpoint + "/external-task/";
  }

  private String getExternalTaskRetriesUri() {
    return getExternalTaskUri() + "retries";
  }

  private String getCountHistoryUri() {
    return engineRestEndpoint + "/history/process-instance/count";
  }

  private String getSuspendProcessInstanceByInstanceIdUri(final String processInstanceId) {
    return engineRestEndpoint + "/process-instance/" + processInstanceId + "/suspended";
  }

  private String getSuspendProcessInstanceByDefinitionUri() {
    return engineRestEndpoint + "/process-instance/suspended";
  }

  private String getSuspendProcessDefinitionByIdUri(final String processDefinitionId) {
    return engineRestEndpoint + "/process-definition/" + processDefinitionId + "/suspended";
  }

  private String getSuspendProcessDefinitionByKeyUri() {
    return engineRestEndpoint + "/process-definition/suspended";
  }

  private String getSuspendProcessInstanceViaBatchUri() {
    return engineRestEndpoint + "/process-instance/suspended-async";
  }

  private String getGetJobUri() {
    return engineRestEndpoint + "/job";
  }

  private String getExecuteJobUri(final String jobId) {
    return engineRestEndpoint + "/job/" + jobId + "/execute";
  }

  private String getSecuredUnclaimTaskUri(final String taskId) {
    return getSecuredEngineUrl() + "/task/" + taskId + "/unclaim";
  }

  private String getSecuredClaimTaskUri(final String taskId) {
    return getSecuredEngineUrl() + "/task/" + taskId + "/claim";
  }

  private String getSecuredCompleteTaskUri(final String taskId) {
    return getSecuredEngineUrl() + "/task/" + taskId + "/complete";
  }

  private String getDeleteIdentityLinkUrl(final String taskId) {
    return engineRestEndpoint + "/task/" + taskId + "/identity-links/delete";
  }

  private CloseableHttpClient createClientWithDefaultBasicAuth() {
    return createClientWithBasicAuth(DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  private CloseableHttpClient createClientWithBasicAuth(final String user, final String password) {
    return HttpClientBuilder.create()
      .setDefaultCredentialsProvider(getBasicCredentialsProvider(user, password)).build();
  }

  private BasicCredentialsProvider getBasicCredentialsProvider(final String user, final String password) {
    final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(user, password));
    return credentialsProvider;
  }

}