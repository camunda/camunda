/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation.generators.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.data.generation.generators.client.dto.MessageCorrelationDto;
import org.camunda.optimize.data.generation.generators.client.dto.TaskDto;
import org.camunda.optimize.data.generation.generators.client.dto.VariableValue;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.rest.engine.dto.DeploymentDto;
import org.camunda.optimize.rest.optimize.dto.ComplexVariableDto;
import org.camunda.optimize.service.util.mapper.CustomDeserializer;
import org.camunda.optimize.service.util.mapper.CustomSerializer;
import org.elasticsearch.common.io.Streams;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_APPLICATION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;

@Slf4j
public class SimpleEngineClient {

  // @formatter:off
  private static final TypeReference<List<TaskDto>> TASK_LIST_TYPE_REFERENCE = new TypeReference<List<TaskDto>>() {};
  private static final String ENGINE_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
  // @formatter:on
  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(ENGINE_DATE_FORMAT);
  private static final int MAX_CONNECTIONS = 150;
  public static final String DELAY_VARIABLE_NAME = "delay";

  private CloseableHttpClient client;
  private String engineRestEndpoint;
  private ObjectMapper objectMapper = new ObjectMapper();
  private static final String[] users = {"mary", "john", "peter"};

  public SimpleEngineClient(String engineRestEndpoint) {
    this.engineRestEndpoint = engineRestEndpoint;
    client = HttpClientBuilder.create()
      .setMaxConnPerRoute(MAX_CONNECTIONS)
      .setMaxConnTotal(MAX_CONNECTIONS)
      .build();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    SimpleDateFormat df = new SimpleDateFormat(ENGINE_DATE_FORMAT);
    objectMapper.setDateFormat(df);
    final JavaTimeModule javaTimeModule = new JavaTimeModule();
    objectMapper.registerModule(javaTimeModule);
    javaTimeModule.addSerializer(OffsetDateTime.class, new CustomSerializer(DATE_TIME_FORMATTER));
    javaTimeModule.addDeserializer(OffsetDateTime.class, new CustomDeserializer(DATE_TIME_FORMATTER));
  }

  @SneakyThrows
  public void initializeStandardUserAuthorizations() {
    for (String user : users) {
      createGrantAllOfTypeAuthorization(RESOURCE_TYPE_APPLICATION, user);
      createGrantAllOfTypeAuthorization(RESOURCE_TYPE_PROCESS_DEFINITION, user);
      createGrantAllOfTypeAuthorization(RESOURCE_TYPE_DECISION_DEFINITION, user);
    }
  }

  @SneakyThrows
  public void createTenantAndRandomlyAuthorizeUsersToIt(final String tenantId) {
    HttpPost createTenantPost = new HttpPost(engineRestEndpoint + "/tenant/create");
    createTenantPost.setEntity(new StringEntity("{\"id\": \"" + tenantId + "\", \"name\": \"" + tenantId + "\"}"));
    createTenantPost.addHeader("Content-Type", "application/json");
    try (CloseableHttpResponse tenantCreatedResponse = client.execute(createTenantPost)) {
      log.info("Created tenant {}.", tenantId);
      for (String user : users) {
        if (ThreadLocalRandom.current().nextBoolean()) {
          HttpPut userTenantPut = new HttpPut(engineRestEndpoint + "/tenant/" + tenantId + "/user-members/" + user);
          try (CloseableHttpResponse userAuthorizationResponse = client.execute(userTenantPut)) {
            log.info("Authorized user {} to tenant {}.", user, tenantId);
          }
        }
      }
    }
  }

  public List<String> deployProcesses(BpmnModelInstance modelInstance, int nVersions, List<String> tenants) {
    List<String> result = new ArrayList<>();
    IntStream.rangeClosed(1, nVersions)
      .mapToObj(n -> deployProcessAndGetId(modelInstance, tenants))
      .collect(Collectors.toList()).forEach(result::addAll);
    return result;
  }

  public void close() {
    try {
      client.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private List<String> deployProcessAndGetId(BpmnModelInstance modelInstance, List<String> tenants) {
    List<DeploymentDto> deploymentDto = deployProcess(modelInstance, tenants);
    return deploymentDto.stream().map(this::getProcessDefinitionId).collect(Collectors.toList());
  }

  public void cleanUpDeployments() {
    log.info("Starting deployments clean up");
    HttpGet get = new HttpGet(getDeploymentUri());
    String responseString;
    List<DeploymentDto> result = null;
    try (CloseableHttpResponse response = client.execute(get)) {
      responseString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
      result = objectMapper.readValue(
        responseString,
        new TypeReference<List<DeploymentDto>>() {
        }
      );
      log.info("Fetched " + result.size() + " deployments");
    } catch (IOException e) {
      log.error("Could fetch deployments from the Engine");
    }
    if (result != null) {
      result.forEach((deployment) -> {
        HttpDelete delete = new HttpDelete(getDeploymentUri() + deployment.getId());
        try {
          URI uri = new URIBuilder(delete.getURI())
            .addParameter("cascade", "true")
            .build();
          delete.setURI(uri);
          client.execute(delete).close();
          log.info("Deleted deployment with id " + deployment.getId());
        } catch (IOException | URISyntaxException e) {
          log.error("Could not delete deployment");
        }
      });
    }
    log.info("Deployment clean up finished");
  }

  private String getProcessDefinitionId(DeploymentDto deployment) {
    List<ProcessDefinitionEngineDto> processDefinitions = getAllProcessDefinitions(deployment);
    if (processDefinitions.size() != 1) {
      log.warn("Deployment should contain only one process definition!");
    }
    return processDefinitions.get(0).getId();
  }

  public VariableValue getProcessInstanceDelayVariable(String procInstId) {
    HttpGet get = new HttpGet(engineRestEndpoint+ "/process-instance/" + procInstId + "/variables/" + DELAY_VARIABLE_NAME);
    VariableValue variable = new VariableValue();
    try (CloseableHttpResponse response = client.execute(get)) {
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      variable = objectMapper.readValue(responseString, VariableValue.class);
    } catch (IOException e) {
      log.error("Error while trying to fetch the variable!!", e);
    }
    return variable;
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

  private String getProcessDefinitionUri() {
    return engineRestEndpoint + "/process-definition";
  }

  private String getCreateDeploymentUri() {
    return getDeploymentUri() + "create";
  }

  private String getDeploymentUri() {
    return engineRestEndpoint + "/deployment/";
  }


  private List<DeploymentDto> deployProcess(BpmnModelInstance bpmnModelInstance, List<String> tenants) {
    String process = Bpmn.convertToString(bpmnModelInstance);
    List<HttpPost> deploymentRequest = createProcessDeploymentRequest(process, tenants);
    return deploymentRequest.stream().map(d -> {
      DeploymentDto deployment = new DeploymentDto();
      try (CloseableHttpResponse response = client.execute(d)) {
        String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
        if (response.getStatusLine().getStatusCode() != 200) {
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

  public void deployDecisionAndGetId(DmnModelInstance modelInstance, List<String> tenants) {
    deployDecisionDefinition(modelInstance, tenants);
  }

  private List<DeploymentDto> deployDecisionDefinition(DmnModelInstance dmnModelInstance, List<String> tenants) {
    String decision = Dmn.convertToString(dmnModelInstance);
    List<HttpPost> deploymentRequest = createDecisionDeploymentRequest(decision, tenants);
    return deploymentRequest.stream().map(d -> {
      DeploymentDto deployment = new DeploymentDto();
      try (CloseableHttpResponse response = client.execute(d)) {
        if (response.getStatusLine().getStatusCode() != 200) {
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


  public void startProcessInstance(String procDefId, Map<String, Object> variables) {
    HttpPost post = new HttpPost(getStartProcessInstanceUri(procDefId));
    post.addHeader("content-type", "application/json");
    post.setEntity(new StringEntity(convertVariableMapToJsonString(variables), StandardCharsets.UTF_8));
    try (CloseableHttpResponse response = client.execute(post)) {
      post.setURI(new URI(post.getURI().toString()));
      if (response.getStatusLine().getStatusCode() != 200) {
        throw new RuntimeException("Could not start the process definition " + procDefId +
                                     ". Reason: " + response.getStatusLine().getReasonPhrase());
      }
    } catch (Exception e) {
      log.error("Error during start of process instance!");
      throw new RuntimeException(e);
    }
  }

  @SneakyThrows
  private String convertVariableMapToJsonString(Map<String, Object> plainVariables) {
    Map<String, Object> variables = new HashMap<>();
    for (Map.Entry<String, Object> nameToValue : plainVariables.entrySet()) {
      Object value = nameToValue.getValue();
      if (value instanceof ComplexVariableDto) {
        variables.put(nameToValue.getKey(), value);
      } else {
        Map<String, Object> fields = new HashMap<>();
        fields.put("value", nameToValue.getValue());
        fields.put("type", nameToValue.getValue().getClass().getSimpleName());
        variables.put(nameToValue.getKey(), fields);
      }
    }
    Map<String, Object> variableWrapper = new HashMap<>();
    variableWrapper.put("variables", variables);
    return objectMapper.writeValueAsString(variableWrapper);
  }

  private String getStartProcessInstanceUri(String procDefId) {
    return engineRestEndpoint + "/process-definition/" + procDefId + "/start";
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
        "hiring_process.bpmn"
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

  public void correlateMessage(String messageName) {
    HttpPost post = new HttpPost(engineRestEndpoint + "/message/");
    post.setHeader("Content-type", "application/json");
    MessageCorrelationDto message = new MessageCorrelationDto();
    message.setAll(true);
    message.setMessageName(messageName);
    StringEntity content;
    try {
      content = new StringEntity(objectMapper.writeValueAsString(message), Charset.defaultCharset());
      post.setEntity(content);
      try (CloseableHttpResponse response = client.execute(post)) {
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != 204) {
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

  @SneakyThrows
  private String serializeDateTimeToUrlEncodedString(final OffsetDateTime createdAfter) {
    return URLEncoder.encode(DATE_TIME_FORMATTER.format(createdAfter), StandardCharsets.UTF_8.name());
  }

  private String getTaskIdentityLinksUri(String taskId) {
    return engineRestEndpoint + "/task/" + taskId + "/identity-links";
  }

  public void unclaimTask(TaskDto task) throws IOException {
    HttpPost unclaimPost = new HttpPost(getUnclaimTaskUri(task.getId()));
    client.execute(unclaimPost).close();
  }

  public void claimTask(TaskDto task) throws IOException {
    HttpPost claimPost = new HttpPost(getClaimTaskUri(task.getId()));
    claimPost.setEntity(new StringEntity("{ \"userId\" : " + "\"demo\"" + "}"));
    claimPost.addHeader("Content-Type", "application/json");
    try (CloseableHttpResponse claimResponse = client.execute(claimPost)) {
      if (claimResponse.getStatusLine().getStatusCode() != 204) {
        throw new RuntimeException("Wrong error code when claiming user tasks!");
      }
    }
  }

  public void addOrRemoveIdentityLinks(TaskDto task) throws IOException {
    HttpGet identityLinksGet = new HttpGet(getTaskIdentityLinksUri(task.getId()));
    try (CloseableHttpResponse getLinksResponse = client.execute(identityLinksGet)) {
      String content = EntityUtils.toString(getLinksResponse.getEntity());
      List<JsonNode> links = objectMapper.readValue(content, new TypeReference<List<JsonNode>>() {
      });

      if (links.size() == 0) {
        HttpPost candidatePost = new HttpPost(getTaskIdentityLinksUri(task.getId()));
        candidatePost.setEntity(
          new StringEntity("{\"userId\":\"demo\", \"type\":\"candidate\"}")
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

  public void completeUserTask(TaskDto task) {
    HttpPost completePost = new HttpPost(getCompleteTaskUri(task.getId()));
    completePost.setEntity(new StringEntity("{}", StandardCharsets.UTF_8));
    completePost.addHeader("Content-Type", "application/json");
    try (CloseableHttpResponse response = client.execute(completePost)) {
      if (response.getStatusLine().getStatusCode() != 204) {
        throw new RuntimeException("Wrong error code when completing user tasks!");
      }
    } catch (Exception e) {
      log.error("Could not complete user task!", e);
    }
  }

  private void createGrantAllOfTypeAuthorization(final int resourceType, final String userId) throws IOException {
    final HttpPost authPost = new HttpPost(engineRestEndpoint + "/authorization/create");
    final AuthorizationDto globalAppAuth = new AuthorizationDto(
      null, 1, Collections.singletonList("ALL"), userId, null, resourceType, "*"
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
      if (createUserResponse.getStatusLine().getStatusCode() != 200) {
        log.warn(IOUtils.toString(createUserResponse.getEntity().getContent(), StandardCharsets.UTF_8));
      }
    }
  }

  private String getClaimTaskUri(String taskId) {
    return engineRestEndpoint + "/task/" + taskId + "/claim";
  }

  private String getUnclaimTaskUri(String taskId) {
    return engineRestEndpoint + "/task/" + taskId + "/unclaim";
  }

  private String getCompleteTaskUri(String taskId) {
    return engineRestEndpoint + "/task/" + taskId + "/complete";
  }


}
