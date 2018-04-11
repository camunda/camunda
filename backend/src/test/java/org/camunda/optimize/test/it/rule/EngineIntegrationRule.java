package org.camunda.optimize.test.it.rule;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
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
import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.rest.engine.dto.DeploymentDto;
import org.camunda.optimize.rest.engine.dto.GroupDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.rest.engine.dto.TaskDto;
import org.camunda.optimize.rest.engine.dto.UserCredentialsDto;
import org.camunda.optimize.rest.engine.dto.UserDto;
import org.camunda.optimize.rest.engine.dto.UserProfileDto;
import org.camunda.optimize.rest.optimize.dto.ComplexVariableDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.CustomDeserializer;
import org.camunda.optimize.service.util.CustomSerializer;
import org.camunda.optimize.test.util.PropertyUtil;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Rule that performs clean up of engine on integration test startup and
 * one more clean up after integration test.
 *
 * Relies on expectation of /purge endpoint available in Tomcat for HTTP GET
 * requests and performing actual purge.
 *
 * @author Askar Akhmerov
 */
public class EngineIntegrationRule extends TestWatcher {

  private static final int MAX_WAIT = 10;
  public static final String COUNT = "count";
  public static final String DEFAULT_PROPERTIES_PATH = "integration-rules.properties";
  private static final CloseableHttpClient closeableHttpClient = HttpClientBuilder.create().build();
  private String propertiesPath;

  private Properties properties;
  private Logger logger = LoggerFactory.getLogger(EngineIntegrationRule.class);

  private ObjectMapper objectMapper;

  public EngineIntegrationRule () {
    this(DEFAULT_PROPERTIES_PATH);
  }

  public EngineIntegrationRule(String propertiesLocation) {
    this.propertiesPath = propertiesLocation;
    properties = PropertyUtil.loadProperties(propertiesPath);
    setupObjectMapper();
  }

  public void init() {
    cleanEngine();
  }

  private void setupObjectMapper() {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(properties.getProperty("camunda.optimize.serialization.date.format"));
    JavaTimeModule javaTimeModule = new JavaTimeModule();
    javaTimeModule.addSerializer(OffsetDateTime.class, new CustomSerializer(formatter));
    javaTimeModule.addDeserializer(OffsetDateTime.class, new CustomDeserializer(formatter));

    objectMapper = Jackson2ObjectMapperBuilder
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

  protected void starting(Description description) {
    this.init();
  }

  protected void finished(Description description) {
    cleanEngine();
  }

  private void cleanEngine() {
    CloseableHttpClient client = getHttpClient();
    HttpGet getRequest = new HttpGet(properties.get("camunda.optimize.test.purge").toString());
    try {
      CloseableHttpResponse response = client.execute(getRequest);
      if (response.getStatusLine().getStatusCode() != 200) {
        throw new RuntimeException("Something really bad happened during purge, " +
            "please check tomcat logs of engine-purge servlet");
      }
      response.close();
    } catch (IOException e) {
      logger.error("Error during purge request", e);
    }
  }

  public void finishAllUserTasks() {
    CloseableHttpClient client = getHttpClient();
    HttpGet get = new HttpGet(getTaskListUri());
    executeFinishAllUserTasks(client, get);
  }

  public void finishAllUserTasks(String processInstanceId) {
    CloseableHttpClient client = getHttpClient();
    HttpGet get = new HttpGet(getTaskListUri());
    URI uri = null;
    try {
      uri = new URIBuilder(get.getURI())
        .addParameter("processInstanceId", processInstanceId)
        .build();
    } catch (URISyntaxException e) {
      logger.error("Could not build uri!", e);
    }
    get.setURI(uri);
    executeFinishAllUserTasks(client, get);
  }

  private void executeFinishAllUserTasks(CloseableHttpClient client, HttpGet get) {
    try {
      CloseableHttpResponse response = client.execute(get);
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      List<TaskDto> tasks = objectMapper.readValue(responseString, new TypeReference<List<TaskDto>>() {
      });
      response.close();
      for (TaskDto task : tasks) {
        claimAndCompleteUserTask(client, task);
      }
    } catch (IOException e) {
      logger.error("Error while trying to finish the user task!!", e);
    }
  }



  private String getTaskListUri() {
    return getEngineUrl() + "/task";
  }

  private void claimAndCompleteUserTask(CloseableHttpClient client, TaskDto task) throws IOException {
    HttpPost claimPost = new HttpPost(getClaimTaskUri(task.getId()));
    claimPost.setEntity(new StringEntity("{ \"userId\" : " + "\"admin\"" + "}"));
    claimPost.addHeader("Content-Type", "application/json");
    CloseableHttpResponse response = client.execute(claimPost);
    if (response.getStatusLine().getStatusCode() != 204) {
      throw new RuntimeException("Could not claim user task!");
    }

    HttpPost completePost = new HttpPost(getCompleteTaskUri(task.getId()));
    completePost.setEntity(new StringEntity("{}"));
    completePost.addHeader("Content-Type", "application/json");
    response.close();
    response = client.execute(completePost);
    if (response.getStatusLine().getStatusCode() != 204) {
      throw new RuntimeException("Could not complete user task!");
    }
    response.close();
  }

  private String getClaimTaskUri(String taskId) {
    return getEngineUrl() + "/task/" + taskId + "/claim";
  }

  private String getCompleteTaskUri(String taskId) {
    return getEngineUrl() + "/task/" + taskId + "/complete";
  }

  public String getProcessDefinitionId() {
    CloseableHttpClient client = getHttpClient();
    HttpRequestBase get = new HttpGet(getProcessDefinitionUri());
    CloseableHttpResponse response;
    try {
      response = client.execute(get);
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      List<ProcessDefinitionEngineDto> procDefs =
        objectMapper.readValue(responseString, new TypeReference<List<ProcessDefinitionEngineDto>>(){});
      response.close();
      assertThat(procDefs.size(), is(1));
      return procDefs.get(0).getId();
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Could not fetch the process definition!", e);
    }
  }

  public ProcessInstanceEngineDto deployAndStartProcessWithVariables(BpmnModelInstance bpmnModelInstance, Map<String, Object> variables) {
    CloseableHttpClient client = getHttpClient();
    DeploymentDto deployment = deployProcess(bpmnModelInstance, client);
    ProcessInstanceEngineDto processInstanceDto = new ProcessInstanceEngineDto();
    try {
      List<ProcessDefinitionEngineDto> procDefs = getAllProcessDefinitions(deployment, client);
      assertThat(procDefs.size(), is(1));

      ProcessDefinitionEngineDto processDefinitionEngineDto = procDefs.get(0);
      processInstanceDto = startProcessInstance(processDefinitionEngineDto.getId(), client, variables);
      processInstanceDto.setProcessDefinitionKey(processDefinitionEngineDto.getKey());
      processInstanceDto.setProcessDefinitionVersion(String.valueOf(processDefinitionEngineDto.getVersion()));

    } catch (IOException e) {
      logger.error("Could not start the given process model!", e);
    }
    return processInstanceDto;
  }

  public HistoricProcessInstanceDto getHistoricProcessInstance(String processInstanceId) {
    CloseableHttpClient client = getHttpClient();
    HttpRequestBase get = new HttpGet(getHistoricGetProcessInstanceUri(processInstanceId));
    HistoricProcessInstanceDto processInstanceDto = new HistoricProcessInstanceDto();
    try {
      CloseableHttpResponse response = client.execute(get);
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      processInstanceDto = objectMapper.readValue(responseString, new TypeReference<HistoricProcessInstanceDto>() {});
      response.close();
    } catch (IOException e) {
      logger.error("Could not get process definition for process instance: " + processInstanceId, e );
    }
    return processInstanceDto;
  }

  public void deleteHistoricProcessInstance(String processInstanceId) {
    CloseableHttpClient client = getHttpClient();
    HttpDelete delete = new HttpDelete(getHistoricGetProcessInstanceUri(processInstanceId));
    try {
      CloseableHttpResponse response = client.execute(delete);
      if(response.getStatusLine().getStatusCode() != 204) {
        logger.error("Could not delete process definition for process instance [{}]. Reason: wrong response code [{}]",
          processInstanceId,
          response.getStatusLine().getStatusCode());
      }
    } catch (Exception e) {
      logger.error("Could not delete process definition for process instance: " + processInstanceId, e );
    }
  }

  public CloseableHttpClient getHttpClient() {
    return closeableHttpClient;
  }

  public ProcessInstanceEngineDto startProcessInstance(String processDefinitionId) {
    CloseableHttpClient client = getHttpClient();
    ProcessInstanceEngineDto processInstanceDto = new ProcessInstanceEngineDto();
    try {
      processInstanceDto = startProcessInstance(processDefinitionId, client, new HashMap<>());
    } catch (IOException e) {
      logger.error("Could not start the given process model!", e);
    }
    return processInstanceDto;
  }

  public ProcessInstanceEngineDto deployAndStartProcess(BpmnModelInstance bpmnModelInstance) {
    return deployAndStartProcessWithVariables(bpmnModelInstance, new HashMap<>());
  }

  public String deployProcessAndGetId(BpmnModelInstance modelInstance) throws IOException {
    ProcessDefinitionEngineDto processDefinitionId = deployProcessAndGetProcessDefinition(modelInstance);
    return processDefinitionId.getId();
  }

  public ProcessDefinitionEngineDto deployProcessAndGetProcessDefinition(BpmnModelInstance modelInstance) throws IOException {
    CloseableHttpClient client = getHttpClient();
    DeploymentDto deploymentDto = deployProcess(modelInstance, client);
    return getProcessDefinitionEngineDto(deploymentDto, client);
  }

  public DeploymentDto deployProcess(BpmnModelInstance bpmnModelInstance, CloseableHttpClient client) {
    String process = Bpmn.convertToString(bpmnModelInstance);
    HttpPost deploymentRequest = createDeploymentRequest(process);
    DeploymentDto deployment = new DeploymentDto();
    try {
      CloseableHttpResponse response = client.execute(deploymentRequest);
      if (response.getStatusLine().getStatusCode() != 200) {
        throw new RuntimeException("Something really bad happened during deployment, " +
          "could not create a deployment!");
      }
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      deployment = objectMapper.readValue(responseString, DeploymentDto.class);
      response.close();
    } catch (IOException e) {
      logger.error("Error during deployment request! Could not deploy the given process model!", e);
    }
    return deployment;
  }

  private HttpPost createDeploymentRequest(String process) {
    HttpPost post = new HttpPost(getDeploymentUri());
    HttpEntity entity = MultipartEntityBuilder
      .create()
      .addTextBody("deployment-name", "deployment")
      .addTextBody("enable-duplicate-filtering", "false")
      .addTextBody("deployment-source", "process application")
      .addBinaryBody("data", process.getBytes(StandardCharsets.UTF_8), ContentType.APPLICATION_OCTET_STREAM, "test.bpmn")
      .build();
    post.setEntity(entity);
    return post;
  }

  private String getDeploymentUri() {
    return getEngineUrl() + "/deployment/create";
  }

  private String getStartProcessInstanceUri(String procDefId) {
    return getEngineUrl() +  "/process-definition/" + procDefId + "/start";
  }

  private String getHistoricGetProcessInstanceUri(String processInstanceId) {
    return getEngineUrl() +  "/history/process-instance/" + processInstanceId;
  }
  private String getProcessDefinitionUri() {
    return getEngineUrl() + "/process-definition";
  }

  private String getCountHistoryUri() {
    return getEngineUrl() + "/history/process-instance/count";
  }

  private String getEngineUrl() {
    return properties.get("camunda.optimize.engine.rest").toString() +
        properties.get("camunda.optimize.engine.name").toString();
  }

  public String getProcessDefinitionId(DeploymentDto deployment, CloseableHttpClient client) throws IOException {
    ProcessDefinitionEngineDto processDefinitionEngineDto = getProcessDefinitionEngineDto(deployment, client);
    return processDefinitionEngineDto.getId();
  }

  private ProcessDefinitionEngineDto getProcessDefinitionEngineDto(DeploymentDto deployment, CloseableHttpClient client) throws IOException {
    List<ProcessDefinitionEngineDto> processDefinitions = getAllProcessDefinitions(deployment, client);
    assertThat("Deployment should contain only one process definition!", processDefinitions.size(), is(1));
    return processDefinitions.get(0);
  }

  public List<ProcessDefinitionEngineDto> getAllProcessDefinitions(DeploymentDto deployment, CloseableHttpClient client) throws IOException {
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
    CloseableHttpResponse response = client.execute(get);
    String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
    List<ProcessDefinitionEngineDto> result = objectMapper.readValue(
        responseString,
        new TypeReference<List<ProcessDefinitionEngineDto>>() {}
        );
    response.close();
    return result;
  }

  public ProcessInstanceEngineDto startProcessInstance(String procDefId, CloseableHttpClient client) throws IOException {
    return startProcessInstance(procDefId, client, new HashMap<>());
  }

  public ProcessInstanceEngineDto startProcessInstance(String processDefinitionId, Map<String, Object> variables) throws IOException {
    CloseableHttpClient client = getHttpClient();
    ProcessInstanceEngineDto processInstanceDto = startProcessInstance(processDefinitionId, client, variables);
    return processInstanceDto;
  }

  private ProcessInstanceEngineDto startProcessInstance(String procDefId, CloseableHttpClient client, Map<String, Object> variables) throws IOException {
    HttpPost post = new HttpPost(getStartProcessInstanceUri(procDefId));
    post.addHeader("Content-Type", "application/json");
    post.setEntity(new StringEntity(convertVariableMapToJsonString(variables), ContentType.APPLICATION_JSON));
    CloseableHttpResponse response = client.execute(post);
    if (response.getStatusLine().getStatusCode() != 200) {
      String body = "";
      if (response.getEntity() != null) {
        body = EntityUtils.toString(response.getEntity());
      }
      throw new RuntimeException(
          "Could not start the process definition. " +
              "Request: [" + post.toString() + "]. " +
              "Response: [" + body + "]"
      );
    }
    String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
    ProcessInstanceEngineDto processInstanceEngineDto =
        objectMapper.readValue(responseString, ProcessInstanceEngineDto.class);
    response.close();
    return processInstanceEngineDto;

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
        fields.put("type", getSimpleName(nameToValue));
        variables.put(nameToValue.getKey(), fields);
      }
    }
    Map<String, Object> variableWrapper = new HashMap<>();
    variableWrapper.put("variables", variables);
    return objectMapper.writeValueAsString(variableWrapper);
  }

  private String getSimpleName(Map.Entry<String, Object> nameToValue) {

    String simpleName = nameToValue.getValue().getClass().getSimpleName();
    if (nameToValue.getValue().getClass().equals(OffsetDateTime.class)) {
      simpleName = Date.class.getSimpleName();
    }
    return simpleName;
  }

  public void waitForAllProcessesToFinish() throws Exception {
    CloseableHttpClient client = getHttpClient();
    boolean done = false;
    HttpRequestBase get = new HttpGet(getCountHistoryUri());
    URI uri = null;
    try {
      uri = new URIBuilder(get.getURI())
          .addParameter("unfinished", "true")
          .build();
    } catch (URISyntaxException e) {
      logger.error("Could not build uri!", e);
    }
    get.setURI(uri);
    int iterations = 0;
    Thread.sleep(100);
    while (!done && iterations < MAX_WAIT) {
      CloseableHttpResponse response = client.execute(get);
      String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
      HashMap<String,Object> parsed = objectMapper.readValue(responseString, new TypeReference<HashMap<String,Object>>() {});
      if (!parsed.containsKey(COUNT)) throw new RuntimeException("Engine could not count PIs");
      if (Integer.valueOf(parsed.get(COUNT).toString()) != 0) {
        Thread.sleep(100);
        iterations = iterations + 1;
      } else {
        done = true;
      }
      response.close();
    }
  }

  public void addUser(String username, String password) {
    UserDto userDto = constructDemoUserDto(username, password);
    try {
      CloseableHttpClient client = getHttpClient();
      HttpPost httpPost = new HttpPost(getEngineUrl() + "/user/create");
      httpPost.addHeader("Content-Type", "application/json");

      httpPost.setEntity(new StringEntity(objectMapper.writeValueAsString(userDto), ContentType.APPLICATION_JSON));
      CloseableHttpResponse response = client.execute(httpPost);
      assertThat(response.getStatusLine().getStatusCode(),is(204));
      response.close();
    } catch (Exception e) {
      logger.error("error creating user", e);
    }

    this.addAuthorizations(username);
  }

  private void addAuthorizations(String username) {
    for (int i = 0; i <15; i++) {
      HashMap<String, Object> values = new HashMap<>();
      values.put("type", 1);
      values.put("permissions", new String [] {"ALL"});
      values.put("userId", username);
      values.put("groupId", null);
      values.put("resourceType", i);
      values.put("resourceId", "*");

      try {
        CloseableHttpClient client = getHttpClient();
        HttpPost httpPost = new HttpPost(getEngineUrl() + "/authorization/create");
        httpPost.addHeader("Content-Type", "application/json");

        httpPost.setEntity(new StringEntity(objectMapper.writeValueAsString(values), ContentType.APPLICATION_JSON));
        CloseableHttpResponse response = client.execute(httpPost);
        assertThat(response.getStatusLine().getStatusCode(),is(200));
        response.close();
      } catch (Exception e) {
        logger.error("error creating authorization", e);
      }
    }

  }

  private UserDto constructDemoUserDto(String username, String password) {
    UserProfileDto profile = new UserProfileDto();
    profile.setEmail("foo@camunda.org");
    profile.setId(username);
    UserCredentialsDto credentials = new UserCredentialsDto();
    credentials.setPassword(password);
    UserDto userDto = new UserDto();
    userDto.setProfile(profile);
    userDto.setCredentials(credentials);
    return userDto;
  }

  public void createGroup(String id, String name, String type) {
    GroupDto groupDto = new GroupDto();
    groupDto.setId(id);
    groupDto.setName(name);
    groupDto.setType(type);

    try {
      CloseableHttpClient client = getHttpClient();
      HttpPost httpPost = new HttpPost(getEngineUrl() + "/group/create");
      httpPost.addHeader("Content-Type", "application/json");

      httpPost.setEntity(new StringEntity(objectMapper.writeValueAsString(groupDto), ContentType.APPLICATION_JSON));
      CloseableHttpResponse response = client.execute(httpPost);
      assertThat(response.getStatusLine().getStatusCode(),is(204));
      response.close();
    } catch (Exception e) {
      logger.error("error creating group", e);
    }
  }

  public void addUserToGroup(String userId, String groupId) {

    try {
      CloseableHttpClient client = getHttpClient();
      HttpPut put = new HttpPut(getEngineUrl() + "/group/" + groupId + "/members/" + userId);
      put.addHeader("Content-Type", "application/json");

      put.setEntity(new StringEntity("", ContentType.APPLICATION_JSON));
      CloseableHttpResponse response = client.execute(put);
      assertThat(response.getStatusLine().getStatusCode(),is(204));
      response.close();
    } catch (Exception e) {
      logger.error("error creating group members", e);
    }
  }
}
