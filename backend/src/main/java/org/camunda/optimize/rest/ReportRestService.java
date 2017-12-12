package org.camunda.optimize.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.result.ReportResultDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.rest.queryparam.adjustment.QueryParamAdjustmentUtil;
import org.camunda.optimize.rest.util.AuthenticationUtil;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.report.ReportEvaluator;
import org.camunda.optimize.service.es.writer.ReportWriter;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.security.TokenService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.util.List;

@Secured
@Path("/report")
@Component
public class ReportRestService {

  private Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private ReportWriter reportWriter;

  @Autowired
  private ReportReader reportReader;

  @Autowired
  private TokenService tokenService;

  @Autowired
  private ReportEvaluator reportEvaluator;

  /**
   * Creates an empty report.
   *
   * @return the id of the report
   */
  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdDto createNewReport(@Context ContainerRequestContext requestContext) {
    String token = AuthenticationUtil.getToken(requestContext);
    String userId = tokenService.getTokenIssuer(token);
    return reportWriter.createNewReportAndReturnId(userId);
  }

  /**
   * Updates the given fields of a report to the given id.
   *
   * @param reportId      the id of the report
   * @param updatedReport report that needs to be updated. Only the fields that are defined here are actually updated.
   */
  @PUT
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateReport(@Context ContainerRequestContext requestContext,
                           @PathParam("id") String reportId,
                           ReportDefinitionDto updatedReport) throws OptimizeException, JsonProcessingException {
    updatedReport.setId(reportId);
    String token = AuthenticationUtil.getToken(requestContext);
    String userId = tokenService.getTokenIssuer(token);
    updatedReport.setLastModifier(userId);
    updatedReport.setLastModified(LocalDateUtil.getCurrentDateTime());
    reportWriter.updateReport(updatedReport);
  }

  /**
   * Get a list of all available reports.
   *
   * @throws IOException If there was a problem retrieving the reports from Elasticsearch.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public List<ReportDefinitionDto> getStoredReports(@Context UriInfo uriInfo) throws IOException {
    List<ReportDefinitionDto> reports = reportReader.getAllReports();
    MultivaluedMap<String, String> queryParameters = uriInfo.getQueryParameters();
    reports = QueryParamAdjustmentUtil.adjustReportResultsToQueryParameters(reports, queryParameters);
    return reports;
  }

  /**
   * Retrieve the report to the specified id.
   */
  @GET
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public ReportDefinitionDto getReport(@PathParam("id") String reportId) throws IOException, OptimizeException {
    return reportReader.getReport(reportId);
  }

  /**
   * Delete the report to the specified id.
   */
  @DELETE
  @Path("/{id}")
  public void deleteReport(@PathParam("id") String reportId) {
    reportWriter.deleteReport(reportId);
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
  public ReportResultDto evaluateReport(@PathParam("id") String reportId) throws IOException, OptimizeException {
    ReportDefinitionDto reportDefinition;
    reportDefinition = reportReader.getReport(reportId);
    ReportResultDto result = reportEvaluator.evaluate(reportDefinition);
    return result;
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
  public ReportResultDto evaluateReport(ReportDataDto reportData) throws IOException, OptimizeException {
    ReportResultDto result = reportEvaluator.evaluate(reportData);
    return result;
  }
}
