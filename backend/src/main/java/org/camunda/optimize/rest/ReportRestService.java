package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.ReportType;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.report.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import java.util.List;

import static org.camunda.optimize.rest.util.AuthenticationUtil.getRequestUser;


@Secured
@Path("/report")
@Component
public class ReportRestService {

  @Autowired
  private ReportService reportService;

  /**
   * Creates an empty new report.
   *
   * @return the id of the report
   */
  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdDto createNewReport(@Context ContainerRequestContext requestContext,
                               ReportDefinitionDto<?> reportDefinitionDto) {
    String userId = getRequestUser(requestContext);
    return reportService.createNewReportAndReturnId(userId, reportDefinitionDto);
  }

  /**
   * Updates the given fields of a report to the given id.
   */
  @PUT
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateReport(@Context ContainerRequestContext requestContext,
                           @PathParam("id") String reportId,
                           @QueryParam("force") boolean force,
                           ReportDefinitionDto updatedReport) throws OptimizeException {
    String userId = getRequestUser(requestContext);
    if (updatedReport instanceof SingleReportDefinitionDto) {
      SingleReportDefinitionDto foo = (SingleReportDefinitionDto) updatedReport;
      if (updatedReport.getReportType().equals(ReportType.PROCESS)) {
        final SingleReportDefinitionDto<SingleReportDataDto> singleReportUpdate =
          (SingleReportDefinitionDto<SingleReportDataDto>) updatedReport;
        reportService.updateSingleProcessReportWithAuthorizationCheck(reportId, singleReportUpdate, userId, force);
      }
    } else {
      final CombinedReportDefinitionDto combinedReportUpdate = (CombinedReportDefinitionDto) updatedReport;
      reportService.updateCombinedProcessReportWithAuthorizationCheck(reportId, combinedReportUpdate, userId, force);
    }
  }

  /**
   * Get a list of all available reports.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<ReportDefinitionDto> getStoredReports(@Context UriInfo uriInfo,
                                                    @Context ContainerRequestContext requestContext) {
    MultivaluedMap<String, String> queryParameters = uriInfo.getQueryParameters();

    String userId = getRequestUser(requestContext);
    return reportService.findAndFilterReports(userId, queryParameters);
  }

  /**
   * Retrieve the report to the specified id.
   */
  @GET
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public ReportDefinitionDto getReport(@Context ContainerRequestContext requestContext,
                                       @PathParam("id") String reportId) {
    String userId = getRequestUser(requestContext);
    return reportService.getReportWithAuthorizationCheck(reportId, userId);
  }

  /**
   * Retrieve the conflicting items that would occur on performing a delete.
   */
  @GET
  @Path("/{id}/delete-conflicts")
  @Produces(MediaType.APPLICATION_JSON)
  public ConflictResponseDto getDeleteConflicts(@Context ContainerRequestContext requestContext,
                                                @PathParam("id") String reportId) {
    String userId = getRequestUser(requestContext);
    return reportService.getReportDeleteConflictingItemsWithAuthorizationCheck(userId, reportId);
  }

  /**
   * Delete the report to the specified id.
   */
  @DELETE
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteReport(@Context ContainerRequestContext requestContext,
                           @PathParam("id") String reportId,
                           @QueryParam("force") boolean force) throws OptimizeException {
    String userId = getRequestUser(requestContext);
    reportService.deleteReportWithAuthorizationCheck(userId, reportId, force);
  }

  /**
   * Retrieves the report definition to the given report id and then
   * evaluate this report and return the result.
   *
   * @param reportId the id of the report
   * @return A report definition that is also containing the actual result of the report evaluation.
   */
  @GET
  @Path("/{id}/evaluate")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public ReportResultDto evaluateReport(@Context ContainerRequestContext requestContext,
                                        @PathParam("id") String reportId) {
    String userId = getRequestUser(requestContext);
    return reportService.evaluateSavedReport(userId, reportId);
  }

  /**
   * Evaluates the given report and returns the result.
   *
   * @return A report definition that is also containing the actual result of the report evaluation.
   */
  @POST
  @Path("/evaluate")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public ReportResultDto evaluateReport(@Context ContainerRequestContext requestContext,
                                        ReportDefinitionDto reportDefinitionDto) {
    String userId = getRequestUser(requestContext);
    return reportService.evaluateReport(userId, reportDefinitionDto);
  }

}
