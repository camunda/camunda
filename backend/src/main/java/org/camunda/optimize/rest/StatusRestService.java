package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.status.ConnectionStatusDto;
import org.camunda.optimize.dto.optimize.query.status.StatusWithProgressDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.status.StatusCheckingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/status")
@Component
public class StatusRestService {

  @Autowired
  private StatusCheckingService statusCheckingService;

  /**
   * Get the status of the connection from Optimize to Elasticsearch and Camunda.
   *
   * @return A DTO containing two booleans showing if Optimize is connected to Elasticsearch and Camunda.
   */
  @GET
  @Path("/connection")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.WILDCARD)
  public ConnectionStatusDto getConnectionStatus() {
    return statusCheckingService.getConnectionStatus();
  }

  /**
   * States how far the import advanced
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public StatusWithProgressDto getImportStatus() {
    return statusCheckingService.getConnectionStatusWithProgress();
  }

}
