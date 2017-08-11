package org.camunda.optimize.service.status;

import org.camunda.optimize.dto.optimize.query.ConnectionStatusDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Component
public class StatusCheckingService {

  @Autowired
  private Client engineClient;

  @Autowired
  private org.elasticsearch.client.Client elasticsearchClient;

  @Autowired
  private ConfigurationService configurationService;


  public ConnectionStatusDto getConnectionStatus() {
    ConnectionStatusDto status = new ConnectionStatusDto();
    status.setConnectedToElasticsearch(isConnectedToElasticSearch());
    status.setConnectedToEngine(isConnectedToEngine());
    return status;
  }

  private boolean isConnectedToEngine() {
    boolean isConnected = false;
    try {
      String endPoint = configurationService.getEngineRestApiEndpoint();
      String engineEndpoint = endPoint + "/engine";
      Response response = engineClient
        .target(engineEndpoint)
        .request(MediaType.APPLICATION_JSON)
        .get();
      isConnected = response.getStatus() == 200;
    } catch (Exception ignored) {
      // do nothing
    }
    return isConnected;
  }

  private boolean isConnectedToElasticSearch() {
    boolean isConnected = false;
    try {
      ClusterHealthResponse getResponse = elasticsearchClient
        .admin()
        .cluster()
        .prepareHealth(configurationService.getOptimizeIndex())
        .get();
      isConnected = getResponse.status().getStatus() == 200 && getResponse.getStatus() != ClusterHealthStatus.RED;
    } catch (Exception ignored) {
      // do nothing
    }
    return isConnected;
  }
}
