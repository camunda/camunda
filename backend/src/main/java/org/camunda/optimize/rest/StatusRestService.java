package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.ConnectionStatusDto;
import org.camunda.optimize.service.status.StatusCheckingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/status")
@Component
public class StatusRestService {

  @Autowired
  @Resource(name = "statusCheckingService")
  private StatusCheckingService statusCheckingService;

  @GET
  @Path("/connection")
  @Produces(MediaType.APPLICATION_JSON)
  public ConnectionStatusDto getConnectionStatus() {
    return statusCheckingService.getConnectionStatus();
  }
}
