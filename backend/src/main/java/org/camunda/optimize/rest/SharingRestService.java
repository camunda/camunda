package org.camunda.optimize.rest;


import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ShareSearchDto;
import org.camunda.optimize.dto.optimize.query.sharing.ShareSearchResultDto;
import org.camunda.optimize.dto.optimize.rest.SharingEnabledDto;
import org.camunda.optimize.dto.optimize.rest.report.EvaluationResultDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.exceptions.SharingNotAllowedException;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.security.SharingService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
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

@Path("/share")
@Component
public class SharingRestService {

  @Autowired
  private SharingService sharingService;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private SessionService sessionService;

  @POST
  @Secured
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  @Path("/report")
  public IdDto createNewReportShare(@Context ContainerRequestContext requestContext,
                                    ReportShareDto createSharingDto) throws SharingNotAllowedException {
    if (configurationService.getSharingEnabled()) {
      String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
      return sharingService.createNewReportShareIfAbsent(createSharingDto, userId);
    } else {
      throw new SharingNotAllowedException("Sharing of reports is disabled per Optimize configuration");
    }
  }

  @POST
  @Secured
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  @Path("/dashboard")
  public IdDto createNewDashboardShare(@Context ContainerRequestContext requestContext,
                                       DashboardShareDto createSharingDto) throws SharingNotAllowedException {
    if (configurationService.getSharingEnabled()) {
      String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
      return sharingService.crateNewDashboardShare(createSharingDto, userId);
    } else {
      throw new SharingNotAllowedException("Sharing of dashboards is disabled per Optimize configuration");
    }
  }

  @GET
  @Secured
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/isEnabled")
  public SharingEnabledDto isSharingEnabled() {
    return new SharingEnabledDto(configurationService.getSharingEnabled());
  }

  @DELETE
  @Secured
  @Path("/report/{shareId}")
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteReportShare(@PathParam("shareId") String reportShareId) {
    sharingService.deleteReportShare(reportShareId);
  }

  @DELETE
  @Secured
  @Path("/dashboard/{shareId}")
  @Produces(MediaType.APPLICATION_JSON)
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
  public EvaluationResultDto<?, ?> evaluateReport(@PathParam("shareId") String reportShareId) {
    return ReportEvaluationResultMapper.mapToEvaluationResultDto(
      sharingService.evaluateReportShare(reportShareId)
    );
  }

  @GET
  @Path("/dashboard/{shareId}/report/{reportId}/evaluate")
  @Produces(MediaType.APPLICATION_JSON)
  public EvaluationResultDto<?, ?> evaluateReport(
    @PathParam("shareId") String dashboardShareId,
    @PathParam("reportId") String reportId
  ) {
    return ReportEvaluationResultMapper.mapToEvaluationResultDto(
      sharingService.evaluateReportForSharedDashboard(dashboardShareId, reportId)
    );
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
   */
  @GET
  @Path("/dashboard/{dashboardId}/isAuthorizedToShare")
  @Produces(MediaType.APPLICATION_JSON)
  public Response evaluateDashboard(@Context ContainerRequestContext requestContext,
                                    @PathParam("dashboardId") String dashboardId) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
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
