package org.camunda.optimize.rest;


import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.sharing.EvaluatedDashboardShareDto;
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
@Path("/share")
@Component
public class SharingRestService {

  @Autowired
  private SharingService sharingService;

  @POST
  @Secured
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdDto createNewShare (SharingDto createSharingDto) {
    sharingService.validate(createSharingDto);
    return sharingService.crateNewShare(createSharingDto);
  }

  @DELETE
  @Secured
  @Path("/{id}")
  public void deleteShare(@PathParam("id") String shareId) {
    sharingService.deleteShare(shareId);
  }

  @GET
  @Secured
  @Path("/report/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public SharingDto findShareForReport(@PathParam("id") String resourceId) {
    return sharingService.findShareForReport(resourceId);
  }

  @GET
  @Secured
  @Path("/dashboard/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public SharingDto findShareForDashboard(@PathParam("id") String resourceId) {
    return sharingService.findShareForDashboard(resourceId);
  }

  @GET
  @Path("/report/{id}/evaluate")
  @Produces(MediaType.APPLICATION_JSON)
  public EvaluatedReportShareDto evaluateReport(@PathParam("id") String shareId) {
    return sharingService.evaluateReport(shareId).orElse(null);
  }

  @GET
  @Path("/dashboard/{id}/evaluate")
  @Produces(MediaType.APPLICATION_JSON)
  public EvaluatedDashboardShareDto evaluateDashboard(@PathParam("id") String shareId) {
    return sharingService.evaluateDashboard(shareId).orElse(null);
  }

}
