/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation.generators.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
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
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.rest.engine.dto.DeploymentDto;
import org.camunda.optimize.rest.optimize.dto.ComplexVariableDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class SimpleEngineClient {

  private final Logger logger = LoggerFactory.getLogger(SimpleEngineClient.class);

  private CloseableHttpClient client;
  private String engineRestEndpoint;
  private ObjectMapper objectMapper = new ObjectMapper();
  private HashSet<String> tasksToNotComplete = new HashSet<>();


  public SimpleEngineClient(String engineRestEndpoint) {
    this.engineRestEndpoint = engineRestEndpoint;
    client = HttpClientBuilder.create().build();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    objectMapper.setDateFormat(df);
  }

  public List<String> deployProcesses(BpmnModelInstance modelInstance, int nVersions) {
    return IntStream.rangeClosed(1, nVersions)
      .mapToObj(n -> deployProcessAndGetId(modelInstance))
      .collect(Collectors.toList());
  }

  public void close() {
    try {
      client.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private String deployProcessAndGetId(BpmnModelInstance modelInstance) {
    DeploymentDto deploymentDto = deployProcess(modelInstance);
    return getProcessDefinitionId(deploymentDto);
  }

  public void cleanUpDeployments() {
    logger.info("Starting deployments clean up");
    HttpGet get = new HttpGet(getDeploymentUri());
    String responseString;
    List<DeploymentDto> result = null;
    try {
      CloseableHttpResponse response = client.execute(get);
      responseString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
      result = objectMapper.readValue(
        responseString,
        new TypeReference<List<DeploymentDto>>() {
        }
      );
      logger.info("Fetched " + result.size() + " deployments");
    } catch (IOException e) {
      logger.error("Could fetch deployments from the Engine");
    }
    if (result != null) {
      result.forEach((deployment) -> {
        HttpDelete delete = new HttpDelete(getDeploymentUri() + deployment.getId());
        try {
          URI uri = new URIBuilder(delete.getURI())
            .addParameter("cascade", "true")
            .build();
          delete.setURI(uri);
          client.execute(delete);
          logger.info("Deleted deployment with id " + deployment.getId());
        } catch (IOException | URISyntaxException e) {
          logger.error("Could not delete deployment");
        }
      });
    }
    logger.info("Deployment clean up finished");
  }

  private String getProcessDefinitionId(DeploymentDto deployment) {
    List<ProcessDefinitionEngineDto> processDefinitions = getAllProcessDefinitions(deployment);
    if (processDefinitions.size() != 1) {
      logger.warn("Deployment should contain only one process definition!");
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
      logger.error("Could not build uri!", e);
    }
    get.setURI(uri);
    CloseableHttpResponse response = null;
    List<ProcessDefinitionEngineDto> result = new ArrayList<>();
    try {
      response = client.execute(get);
      String responseString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
      result = objectMapper.readValue(
        responseString,
        new TypeReference<List<ProcessDefinitionEngineDto>>() {
        }
      );
    } catch (Exception e) {
      logger.error("Could not fetch all process definitions for given deployment!", e);
    } finally {
      closeResponse(response);
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


  private DeploymentDto deployProcess(BpmnModelInstance bpmnModelInstance) {
    String process = Bpmn.convertToString(bpmnModelInstance);
    HttpPost deploymentRequest = createProcessDeploymentRequest(process);
    DeploymentDto deployment = new DeploymentDto();
    CloseableHttpResponse response = null;
    try {
      response = client.execute(deploymentRequest);
      if (response.getStatusLine().getStatusCode() != 200) {
        throw new RuntimeException("Something really bad happened during deployment, " +
                                     "could not create a deployment!");
      }
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      deployment = objectMapper.readValue(responseString, DeploymentDto.class);
      response.close();
    } catch (IOException e) {
      logger.error("Error during deployment request! Could not deploy the given process model!", e);
    } finally {
      closeResponse(response);
    }
    return deployment;
  }

  public void deployDecisionAndGetId(DmnModelInstance modelInstance) {
    deployDecisionDefinition(modelInstance);
  }

  private DeploymentDto deployDecisionDefinition(DmnModelInstance dmnModelInstance) {
    String decision = Dmn.convertToString(dmnModelInstance);
    HttpPost deploymentRequest = createDecisionDeploymentRequest(decision);
    DeploymentDto deployment = new DeploymentDto();
    CloseableHttpResponse response = null;
    try {
      response = client.execute(deploymentRequest);
      if (response.getStatusLine().getStatusCode() != 200) {
        throw new RuntimeException("Something really bad happened during deployment, " +
                                     "could not create a deployment!");
      }
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      deployment = objectMapper.readValue(responseString, DeploymentDto.class);
      response.close();
    } catch (IOException e) {
      logger.error("Error during deployment request! Could not deploy the given dmn model!", e);
    } finally {
      closeResponse(response);
    }
    return deployment;
  }

  public void startProcessInstance(String procDefId, Map<String, Object> variables) {
    CloseableHttpResponse response = null;
    try {
      HttpPost post = new HttpPost(getStartProcessInstanceUri(procDefId));
      post.addHeader("content-type", "application/json");
      post.setEntity(new StringEntity(convertVariableMapToJsonString(variables)));
      response = client.execute(post);

      if (response.getStatusLine().getStatusCode() != 200) {
        throw new RuntimeException("Could not start the process definition " + procDefId +
                                     ". Reason: " + response.getStatusLine().getReasonPhrase());
      }
    } catch (Exception e) {
      logger.error("Error during start of process instance!");
      throw new RuntimeException(e);
    } finally {
      closeResponse(response);
    }
  }

  private String convertVariableMapToJsonString(Map<String, Object> plainVariables) throws JsonProcessingException {
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

  private HttpPost createProcessDeploymentRequest(String process) {
    HttpPost post = new HttpPost(getCreateDeploymentUri());
    HttpEntity entity = MultipartEntityBuilder
      .create()
      .addTextBody("deployment-name", "deployment")
      .addTextBody("enable-duplicate-filtering", "false")
      .addTextBody("deployment-source", "process application")
      .addBinaryBody(
        "data",
        process.getBytes(StandardCharsets.UTF_8),
        ContentType.APPLICATION_OCTET_STREAM,
        "hiring_process.bpmn"
      )
      .build();
    post.setEntity(entity);
    return post;
  }

  private HttpPost createDecisionDeploymentRequest(String decision) {
    HttpPost post = new HttpPost(getCreateDeploymentUri());
    HttpEntity entity = MultipartEntityBuilder
      .create()
      .addTextBody("deployment-name", "deployment")
      .addTextBody("enable-duplicate-filtering", "false")
      .addTextBody("deployment-source", "process application")
      .addBinaryBody(
        "data",
        decision.getBytes(StandardCharsets.UTF_8),
        ContentType.APPLICATION_OCTET_STREAM,
        "decision.dmn"
      )
      .build();
    post.setEntity(entity);
    return post;
  }

  public void correlateMessage(String messageName) {
    HttpPost post = new HttpPost(engineRestEndpoint + "/message/");
    post.setHeader("Content-type", "application/json");
    MessageCorrelationDto message = new MessageCorrelationDto();
    message.setAll(true);
    message.setMessageName(messageName);
    StringEntity content;
    CloseableHttpResponse response = null;
    try {
      content = new StringEntity(objectMapper.writeValueAsString(message), Charset.defaultCharset());
      post.setEntity(content);
      response = client.execute(post);
      int statusCode = response.getStatusLine().getStatusCode();
      if (statusCode != 204) {
        throw new RuntimeException("Warning: response code for correlating message should be 204, got " + statusCode
                                     + " instead");
      }
    } catch (Exception e) {
      logger.error("Error while trying to correlate message!", e);
    } finally {
      closeResponse(response);
    }
  }

  private void closeResponse(CloseableHttpResponse response) {
    if (response != null) {
      try {
        response.close();
      } catch (IOException e) {
        logger.error("Can't close response", e);
      }
    }
  }

  public boolean finishAllUserTasks() {
    HttpGet get = new HttpGet(getTaskListUri());
    return executeFinishAllUserTasks(client, get);
  }

  private boolean executeFinishAllUserTasks(CloseableHttpClient client, HttpGet get) {

    try (CloseableHttpResponse response = client.execute(get)) {
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      List<TaskDto> tasks = objectMapper.readValue(responseString, new TypeReference<List<TaskDto>>() {
      });

      if (allTasksCompleted(tasks)) {
        return true;
      }

      if (tasks.size() != 0)
        logger.info(tasks.size() - tasksToNotComplete.size() + " user tasks left to complete");

      for (TaskDto task : tasks) {
        claimAndCompleteUserTask(client, task);
      }
      return false;
    } catch (Exception e) {
      logger.error("Error while trying to finish the user task!!", e);
      return false;
    }
  }

  private boolean allTasksCompleted(List<TaskDto> tasks) {
    return tasks.size() != 0 && tasks.size() == tasksToNotComplete.size() &&
    tasks.stream().allMatch(t -> tasksToNotComplete.contains(t.getId()));
  }


  private String getTaskListUri() {
    return engineRestEndpoint + "/task?maxResults=1000";
  }

  private String getTaskIdentityLinksUri(String taskId) {
    return engineRestEndpoint + "/task/" + taskId + "/identity-links";
  }


  private void claimAndCompleteUserTask(CloseableHttpClient client, TaskDto task) {
    Random random = new Random();
    try {
      addOrRemoveIdentityLinks(client, task);
      claimTask(client, task);

      if (random.nextDouble() > 0.95) {
        unclaimTask(client, task);
      } else if (!isTaskToComplete(task.getId())) {
        if (random.nextDouble() < 0.97) {
          completeUserTask(client, task);
        } else {
          setDoNotCompleteFlag(task);
        }
      }
    } catch (Exception e) {
      logger.error("Could not claim user task!", e);
    }
  }

  private void unclaimTask(CloseableHttpClient client, TaskDto task) throws IOException {
    HttpPost unclaimPost = new HttpPost(getUnclaimTaskUri(task.getId()));
    CloseableHttpResponse response = client.execute(unclaimPost);
    closeResponse(response);
  }

  private void claimTask(CloseableHttpClient client, TaskDto task) throws IOException {
    HttpPost claimPost = new HttpPost(getClaimTaskUri(task.getId()));
    claimPost.setEntity(new StringEntity("{ \"userId\" : " + "\"demo\"" + "}"));
    claimPost.addHeader("Content-Type", "application/json");
    CloseableHttpResponse claimResponse = client.execute(claimPost);

    if (claimResponse.getStatusLine().getStatusCode() != 204) {
      throw new RuntimeException("Wrong error code when claiming user tasks!");
    }

    closeResponse(claimResponse);
  }

  private void addOrRemoveIdentityLinks(CloseableHttpClient client, TaskDto task) throws IOException {
    HttpGet identityLinksGet = new HttpGet(getTaskIdentityLinksUri(task.getId()));
    CloseableHttpResponse getLinksResponse = client.execute(identityLinksGet);
    String content = EntityUtils.toString(getLinksResponse.getEntity());
    List<JsonNode> links = objectMapper.readValue(content, new TypeReference<List<JsonNode>>() {
    });

    closeResponse(getLinksResponse);
    if (links.size() == 0) {
      HttpPost candidatePost = new HttpPost(getTaskIdentityLinksUri(task.getId()));
      candidatePost.setEntity(
        new StringEntity("{\"userId\":\"demo\", \"type\":\"candidate\"}")
      );
      candidatePost.addHeader("Content-Type", "application/json");
      client.execute(candidatePost);
    } else {
      HttpPost candidateDeletePost = new HttpPost(getTaskIdentityLinksUri(task.getId()) + "/delete");
      candidateDeletePost.addHeader("Content-Type", "application/json");
      candidateDeletePost.setEntity(new StringEntity(objectMapper.writeValueAsString(links.get(0))));
      client.execute(candidateDeletePost);
    }
  }

  private void setDoNotCompleteFlag(TaskDto task) throws IOException {
    tasksToNotComplete.add(task.getId());
  }

  private Boolean isTaskToComplete(String taskId) {
    return tasksToNotComplete.contains(taskId);
  }

  private void completeUserTask(CloseableHttpClient client, TaskDto task) {
    CloseableHttpResponse response = null;
    try {
      HttpPost completePost = new HttpPost(getCompleteTaskUri(task.getId()));
      completePost.setEntity(new StringEntity("{}"));
      completePost.addHeader("Content-Type", "application/json");
      response = client.execute(completePost);
      if (response.getStatusLine().getStatusCode() != 204) {
        throw new RuntimeException("Wrong error code when completing user tasks!");
      }
    } catch (Exception e) {
      logger.error("Could not complete user task!", e);
    } finally {
      closeResponse(response);
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
