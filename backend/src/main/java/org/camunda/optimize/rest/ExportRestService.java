package org.camunda.optimize.rest;

import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.export.ExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.camunda.optimize.rest.util.AuthenticationUtil.getRequestUser;

/**
 * @author Askar Akhmerov
 */

@Path("/export")
@Secured
@Component
public class ExportRestService {
  @Autowired
  private ExportService exportService;

  @GET
  @Path("csv/{reportId}/{fileName}")
  public Response getCsvReport (
      @Context ContainerRequestContext requestContext,
      @PathParam("reportId") String reportId,
      @PathParam("fileName") String fileName
  ) {
    String userId = getRequestUser(requestContext);
    String resultFileName = fileName == null ? System.currentTimeMillis() + ".csv" : fileName;
    return Response.ok(exportService.getCSVForReport(userId, reportId), MediaType.APPLICATION_OCTET_STREAM)
        .header("Content-Disposition", "attachment; filename=" + resultFileName)
        .build();
  }
}
