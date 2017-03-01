package org.camunda.optimize.test.rule;

import com.fasterxml.jackson.core.type.TypeReference;
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
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.rest.engine.dto.DeploymentDto;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.test.util.PropertyUtil;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

/**
 * Rule that performs clean up of engine on integration test startup and
 * one more clean up after integration test.
 *
 * Relies on expectation of /purge endpoint available in Tomcat for HTTP GET
 * requests and performing actual purge.
 *
 * @author Askar Akhmerov
 */
@Component
public class EngineIntegrationRule extends TestWatcher {

  @Autowired
  ConfigurationService configurationService;

  private Properties properties;
  private Logger logger = LoggerFactory.getLogger(EngineIntegrationRule.class);

  @Autowired
  private ObjectMapper objectMapper;

  @PostConstruct
  public void init() {
    properties = PropertyUtil.loadProperties("service-it.properties");
    cleanEngine();
  }

  @Override
  protected void finished(Description description) {
    cleanEngine();
  }

  private void cleanEngine() {
    CloseableHttpClient client = HttpClientBuilder.create().build();
    HttpGet getRequest = new HttpGet(properties.get("camunda.optimize.test.purge").toString());
    try {
      CloseableHttpResponse response = client.execute(getRequest);
      if (response.getStatusLine().getStatusCode() != 200) {
        throw new RuntimeException("Something really bad happened during purge, " +
          "please check tomcat logs of engine-purge servlet");
      }
      client.close();
    } catch (IOException e) {
      logger.error("Error during purge request", e);
    }

  }

  public void deployAndStartProcess(BpmnModelInstance bpmnModelInstance) {
    CloseableHttpClient client = HttpClientBuilder.create().build();
    DeploymentDto deployment = deployProcess(bpmnModelInstance, client);
    try {
      List<ProcessDefinitionEngineDto> procDefs = getAllProcessDefinitions(deployment, client);
      for (ProcessDefinitionEngineDto procDef : procDefs) {
        startProcessInstance(procDef.getId(), client);
      }
      client.close();
    } catch (IOException e) {
      logger.error("Could not start the given process model!");
      e.printStackTrace();
    }
  }

  private DeploymentDto deployProcess(BpmnModelInstance bpmnModelInstance, CloseableHttpClient client) {
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
    } catch (IOException e) {
      logger.error("Error during deployment request! Could not deploy the given process model!");
      e.printStackTrace();
    }
    return deployment;
  }

  private HttpPost createDeploymentRequest(String process) {
    HttpPost post = new HttpPost(configurationService.getEngineRestApiEndpointOfCustomEngine() + "/deployment/create");
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

  private List<ProcessDefinitionEngineDto> getAllProcessDefinitions(DeploymentDto deployment, CloseableHttpClient client) throws IOException {
    HttpRequestBase get = new HttpGet(configurationService.getEngineRestApiEndpointOfCustomEngine() + "/process-definition");
    URI uri = null;
    try {
      uri = new URIBuilder(get.getURI())
        .addParameter("deploymentId", deployment.getId())
        .build();
    } catch (URISyntaxException e) {
      logger.error("Could not build uri!");
      e.printStackTrace();
    }
    get.setURI(uri);
    CloseableHttpResponse response = client.execute(get);
    String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
    return objectMapper.readValue(responseString, new TypeReference<List<ProcessDefinitionEngineDto>>(){});
  }

  private void startProcessInstance(String procDefId, CloseableHttpClient client) throws IOException {
    HttpPost post = new HttpPost(configurationService.getEngineRestApiEndpointOfCustomEngine() +
      "/process-definition/" + procDefId + "/start");
    post.addHeader("content-type", "application/json");
    post.setEntity(new StringEntity("{}"));
    CloseableHttpResponse response = client.execute(post);
    if (response.getStatusLine().getStatusCode() != 200) {
      throw new RuntimeException("Could not start the process definition " + procDefId +
      ". Reason: " + response.getStatusLine().getReasonPhrase());
    }
    // we need to release the connection so no error is thrown
    response.getEntity().getContent().close();
  }

}
