package org.camunda.optimize.rest;


import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.result.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareDto;
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
    return sharingService.createNewReportShareIfAbsent(createSharingDto);
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
  @Path("/report/{shareId}")
  public void deleteReportShare(@PathParam("shareId") String reportShareId) {
    sharingService.deleteReportShare(reportShareId);
  }

  @DELETE
  @Secured
  @Path("/dashboard/{shareId}")
  public void deleteDashboardShare(@PathParam("shareId") String dashboardShareId) {
    sharingService.deleteDashboardShare(dashboardShareId);
  }

  @GET
  @Secured
  @Path("/report/{reportId}")
  @Produces(MediaType.APPLICATION_JSON)
  public ReportShareDto findShareForReport(@PathParam("reportId") String reportId) {
    return sharingService.findShareForReport(reportId).orElse(null);
  }

  @GET
  @Secured
  @Path("/dashboard/{dashboardId}")
  @Produces(MediaType.APPLICATION_JSON)
  public DashboardShareDto findShareForDashboard(@PathParam("dashboardId") String dashboardId) {
    return sharingService.findShareForDashboard(dashboardId).orElse(null);
  }

  @GET
  @Path("/report/{shareId}/evaluate")
  @Produces(MediaType.APPLICATION_JSON)
  public ReportResultDto evaluateReport(@PathParam("shareId") String reportShareId) {
    return sharingService.evaluateReport(reportShareId);
  }

  @GET
  @Path("/dashboard/{shareId}/report/{reportId}/evaluate")
  @Produces(MediaType.APPLICATION_JSON)
  public ReportResultDto evaluateReport(@PathParam("shareId") String dashboardShareId,
                                        @PathParam("reportId") String reportId) {
    return sharingService.evaluateReportForSharedDashboard(dashboardShareId, reportId);
  }

  @GET
  @Path("/dashboard/{shareId}/evaluate")
  @Produces(MediaType.APPLICATION_JSON)
  public DashboardDefinitionDto evaluateDashboard(@PathParam("shareId") String dashboardShareId) {
    return sharingService.evaluateDashboard(dashboardShareId).orElse(null);
  }

}
