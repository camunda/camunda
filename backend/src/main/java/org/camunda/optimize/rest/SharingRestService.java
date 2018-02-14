package org.camunda.optimize.rest;


import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.EvaluatedDashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.EvaluatedReportShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;
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
  @Path("/report")
  public IdDto createNewReportShare(ReportShareDto createSharingDto) {
    sharingService.validateReportShare(createSharingDto);
    return sharingService.crateNewReportShare(createSharingDto);
  }

  @POST
  @Secured
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  @Path("/dashboard")
  public IdDto createNewDashboardShare (DashboardShareDto createSharingDto) {
    sharingService.validateDashboardShare(createSharingDto);
    return sharingService.crateNewDashboardShare(createSharingDto);
  }

  @DELETE
  @Secured
  @Path("/report/{id}")
  public void deleteReportShare(@PathParam("id") String shareId) {
    sharingService.deleteReportShare(shareId);
  }

  @DELETE
  @Secured
  @Path("/dashboard/{id}")
  public void deleteDashboardShare(@PathParam("id") String shareId) {
    sharingService.deleteDashboardShare(shareId);
  }

  @GET
  @Secured
  @Path("/report/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public ReportShareDto findShareForReport(@PathParam("id") String resourceId) {
    return sharingService.findShareForReport(resourceId);
  }

  @GET
  @Secured
  @Path("/dashboard/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public DashboardShareDto findShareForDashboard(@PathParam("id") String resourceId) {
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
