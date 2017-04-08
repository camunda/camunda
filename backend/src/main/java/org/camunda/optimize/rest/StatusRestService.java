package org.camunda.optimize.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.ConnectionStatusDto;
import org.camunda.optimize.dto.optimize.ProgressDto;
import org.camunda.optimize.service.status.ImportProgressReporter;
import org.camunda.optimize.service.status.StatusCheckingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/status")
@Component
public class StatusRestService {

  @Autowired
  private StatusCheckingService statusCheckingService;

  @Autowired
  private ImportProgressReporter importProgressReporter;

  @Autowired
  private ObjectMapper objectMapper;

  /**
   * Get the status of the connection from Optimize to Elasticsearch and Camunda.
   *
   * @return A DTO containing two booleans showing if Optimize is connected to Elasticsearch and Camunda.
   */
  @GET
  @Path("/connection")
  @Produces(MediaType.APPLICATION_JSON)
  public ConnectionStatusDto getConnectionStatus() {
    return statusCheckingService.getConnectionStatus();
  }

  /**
   * States how far the import advanced.
   *
   * @return A DTO containing a number between 0 and 100 showing the progress.
   */
  @GET
  @Path("/import-progress")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getImportProgress() {
    try {
      ProgressDto progressDto = new ProgressDto();
      progressDto.setProgress(importProgressReporter.computeImportProgress());
      return Response.ok(objectMapper.writeValueAsString(progressDto), MediaType.APPLICATION_JSON).build();
    } catch (Exception e) {
      return Response
        .serverError()
        .entity("{ \"errorMessage\" : \"It was not possible to compute the import progress. Reason: " +
          e.getMessage() + "\"}").build();
    }
  }

}
