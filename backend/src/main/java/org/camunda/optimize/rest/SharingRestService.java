package org.camunda.optimize.rest;


import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ShareSearchDto;
import org.camunda.optimize.dto.optimize.query.sharing.ShareSearchResultDto;
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
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.camunda.optimize.rest.util.AuthenticationUtil.getRequestUser;

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
  public IdDto createNewReportShare(@Context ContainerRequestContext requestContext,
                                    ReportShareDto createSharingDto) {
    String userId = getRequestUser(requestContext);
    return sharingService.createNewReportShareIfAbsent(createSharingDto, userId);
  }

  @POST
  @Secured
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  @Path("/dashboard")
  public IdDto createNewDashboardShare (@Context ContainerRequestContext requestContext,
                                        DashboardShareDto createSharingDto) {
    String userId = getRequestUser(requestContext);
    return sharingService.crateNewDashboardShare(createSharingDto, userId);
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
    return sharingService.evaluateReportShare(reportShareId);
  }

  @GET
  @Path("/dashboard/{shareId}/report/{reportId}/evaluate")
  @Produces(MediaType.APPLICATION_JSON)
  public ReportResultDto evaluateReport(
    @PathParam("shareId") String dashboardShareId,
    @PathParam("reportId") String reportId
  ) {
    return sharingService.evaluateReportForSharedDashboard(dashboardShareId, reportId);
  }

  @GET
  @Path("/dashboard/{shareId}/evaluate")
  @Produces(MediaType.APPLICATION_JSON)
  public DashboardDefinitionDto evaluateDashboard(@PathParam("shareId") String dashboardShareId) {
    return sharingService.evaluateDashboard(dashboardShareId).orElse(null);
  }

  /**
   * Returns status code
   * - 200: if user that performs the request is allowed to share the dashboard
   * - 403: if the user does not have the authorization to share the dashboard
   * - 404: if the dashboard for the id does not exist
   * - 500: if there were problems checking the authorizations.
   *
   */
  @GET
  @Path("/dashboard/{dashboardId}/isAuthorizedToShare")
  @Produces(MediaType.APPLICATION_JSON)
  public Response evaluateDashboard(@Context ContainerRequestContext requestContext,
                                    @PathParam("dashboardId") String dashboardId) {
    String userId = getRequestUser(requestContext);
    sharingService.validateAndCheckAuthorization(dashboardId, userId);
    // if no error was thrown
    return Response.status(200).entity("OK").build();
  }

  @POST
  @Path("/status")
  @Secured
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public ShareSearchResultDto checkShareStatus(ShareSearchDto searchRequest) {
    return sharingService.checkShareStatus(searchRequest);
  }
}
