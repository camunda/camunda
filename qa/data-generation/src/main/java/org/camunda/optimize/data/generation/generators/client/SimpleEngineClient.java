package org.camunda.optimize.data.generation.generators.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class SimpleEngineClient {

  private final Logger logger = LoggerFactory.getLogger(SimpleEngineClient.class);

  private CloseableHttpClient client;
  private String engineRestEndpoint;
  private ObjectMapper objectMapper = new ObjectMapper();


  public SimpleEngineClient(String engineRestEndpoint) {
    this.engineRestEndpoint = engineRestEndpoint;
    client = HttpClientBuilder.create().build();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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
        new TypeReference<List<ProcessDefinitionEngineDto>>() {}
        );
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      closeResponse(response);
    }

    return result;
  }

  private String getProcessDefinitionUri() {
    return  engineRestEndpoint + "/process-definition";
  }

  private String getDeploymentUri() {
    return engineRestEndpoint + "/deployment/create";
  }

  private DeploymentDto deployProcess(BpmnModelInstance bpmnModelInstance) {
    String process = Bpmn.convertToString(bpmnModelInstance);
    HttpPost deploymentRequest = createDeploymentRequest(process);
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

  public void startProcessInstance(String procDefId, Map<String, Object> variables) throws IOException {
    HttpPost post = new HttpPost(getStartProcessInstanceUri(procDefId));
    post.addHeader("content-type", "application/json");
    post.setEntity(new StringEntity(convertVariableMapToJsonString(variables)));
    CloseableHttpResponse response = client.execute(post);
    if (response.getStatusLine().getStatusCode() != 200) {
      throw new RuntimeException("Could not start the process definition " + procDefId +
      ". Reason: " + response.getStatusLine().getReasonPhrase());
    }
    response.close();
  }

  private String convertVariableMapToJsonString(Map<String, Object> plainVariables) throws JsonProcessingException {
    Map<String, Object> variables = new HashMap<>();
    for (Map.Entry<String, Object> nameToValue : plainVariables.entrySet()) {
      Object value = nameToValue.getValue();
      if(value instanceof ComplexVariableDto) {
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
    return engineRestEndpoint +  "/process-definition/" + procDefId + "/start";
  }

  private HttpPost createDeploymentRequest(String process) {
    HttpPost post = new HttpPost(getDeploymentUri());
    HttpEntity entity = MultipartEntityBuilder
      .create()
      .addTextBody("deployment-name", "deployment")
      .addTextBody("enable-duplicate-filtering", "false")
      .addTextBody("deployment-source", "process application")
      .addBinaryBody("data", process.getBytes(StandardCharsets.UTF_8), ContentType.APPLICATION_OCTET_STREAM, "hiring_process.bpmn")
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
      if (response.getStatusLine().getStatusCode() != 204) {
        System.out.println("Warning: Code for send candidate replied should be 204!");
      }
    } catch (IOException e) {
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

  public void finishAllUserTasks() {
    HttpGet get = new HttpGet(getTaskListUri());
    executeFinishAllUserTasks(client, get);
  }

  private void executeFinishAllUserTasks(CloseableHttpClient client, HttpGet get) {
    CloseableHttpResponse response = null;
    try {
      response = client.execute(get);
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      List<TaskDto> tasks = objectMapper.readValue(responseString, new TypeReference<List<TaskDto>>() {
      });
      for (TaskDto task : tasks) {
        claimAndCompleteUserTask(client, task);
      }
    } catch (IOException e) {
      logger.error("Error while trying to finish the user task!!", e);
    } finally {
      closeResponse(response);
    }
  }



  private String getTaskListUri() {
    return engineRestEndpoint + "/task";
  }

  private void claimAndCompleteUserTask(CloseableHttpClient client, TaskDto task) throws IOException {
    HttpPost claimPost = new HttpPost(getClaimTaskUri(task.getId()));
    claimPost.setEntity(new StringEntity("{ \"userId\" : " + "\"demo\"" + "}"));
    claimPost.addHeader("Content-Type", "application/json");
    CloseableHttpResponse response = client.execute(claimPost);
    if (response.getStatusLine().getStatusCode() != 204) {
      logger.error("Could not claim user task!");
    }

    HttpPost completePost = new HttpPost(getCompleteTaskUri(task.getId()));
    completePost.setEntity(new StringEntity("{}"));
    completePost.addHeader("Content-Type", "application/json");
    response.close();
    response = client.execute(completePost);
    if (response.getStatusLine().getStatusCode() != 204) {
      logger.error("Could not complete user task!");
    }
    response.close();
  }

  private String getClaimTaskUri(String taskId) {
    return engineRestEndpoint + "/task/" + taskId + "/claim";
  }

  private String getCompleteTaskUri(String taskId) {
    return engineRestEndpoint + "/task/" + taskId + "/complete";
  }


}
