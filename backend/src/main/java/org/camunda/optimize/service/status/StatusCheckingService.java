package org.camunda.optimize.service.status;

import org.camunda.optimize.dto.engine.ProcessEngineDto;
import org.camunda.optimize.dto.optimize.query.ConnectionStatusDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class StatusCheckingService {


  @Autowired
  private org.elasticsearch.client.Client elasticsearchClient;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private EngineContextFactory engineContextFactory;


  public ConnectionStatusDto getConnectionStatus() {
    ConnectionStatusDto status = new ConnectionStatusDto();
    status.setConnectedToElasticsearch(isConnectedToElasticSearch());
    Map<String, Boolean> engineConnections = new HashMap<>();
    for (EngineContext e : engineContextFactory.getConfiguredEngines()) {
      engineConnections.put(e.getEngineAlias(), isConnectedToEngine(e));
    }
    status.setEngineConnections(engineConnections);
    return status;
  }

  private boolean isConnectedToEngine(EngineContext engineContext) {
    boolean isConnected = false;
    try {
      String endPoint = configurationService.getEngineRestApiEndpoint(engineContext.getEngineAlias());
      String engineEndpoint = endPoint + "/engine";
      Response response = engineContext.getEngineClient()
        .target(engineEndpoint)
        .request(MediaType.APPLICATION_JSON)
        .get();
      boolean hasCorrectResponseCode = response.getStatus() == 200;
      boolean engineIsRunning = engineWithEngineNameIsRunning(response, configurationService.getEngineName(engineContext.getEngineAlias()));
      isConnected = hasCorrectResponseCode && engineIsRunning;
    } catch (Exception ignored) {
      // do nothing
    }
    return isConnected;
  }

  private boolean engineWithEngineNameIsRunning(Response response, String engineName) {
    List<ProcessEngineDto> engineNames = response.readEntity(new GenericType<List<ProcessEngineDto>>() {});
    return engineNames.stream().anyMatch(e -> e.getName().equals(engineName));
  }

  private boolean isConnectedToElasticSearch() {
    boolean isConnected = false;
    try {
      ClusterHealthResponse getResponse = elasticsearchClient
        .admin()
        .cluster()
        .prepareHealth()
        .get();
      isConnected = getResponse.status().getStatus() == 200 && getResponse.getStatus() != ClusterHealthStatus.RED;
    } catch (Exception ignored) {
      // do nothing
    }
    return isConnected;
  }


  public EngineContextFactory getEngineContextFactory() {
    return engineContextFactory;
  }

  public void setEngineContextFactory(EngineContextFactory engineContextFactory) {
    this.engineContextFactory = engineContextFactory;
  }
}
