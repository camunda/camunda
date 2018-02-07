package org.camunda.optimize.rest;


import org.camunda.optimize.dto.optimize.query.sharing.EvaluatedReportShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.SharingDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.security.SharingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * @author Askar Akhmerov
 */
@Secured
@Path("/share")
@Component
public class SharingRestService {

  @Autowired
  private SharingService sharingService;

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public String createNewShare (SharingDto createSharingDto) {
    return sharingService.crateNewShare(createSharingDto);
  }

  @DELETE
  @Path("/{id}")
  public void deleteAlert(@PathParam("id") String shareId) {
    sharingService.deleteShare(shareId);
  }

  @GET
  @Path("/report/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public SharingDto findShareForResource(@PathParam("id") String resourceId) {
    return sharingService.findShareForResource(resourceId);
  }

  @GET
  @Path("/report/{id}/evaluate")
  @Produces(MediaType.APPLICATION_JSON)
  public EvaluatedReportShareDto evaluateReport(@PathParam("id") String shareId) {
    return sharingService.evaluate(shareId).orElse(null);
  }

}
