package org.camunda.optimize.rest;

import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.export.ExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

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
  @Path("/csv/{reportId}/{fileName}")
  public Response getCsvReport (@PathParam("reportId") String reportId, @PathParam("fileName") String fileName) {
    String resultFileName = fileName == null ? System.currentTimeMillis() + ".csv" : fileName;
    return Response.ok(exportService.getCSVForReport(reportId), MediaType.APPLICATION_OCTET_STREAM)
        .header("Content-Disposition", "attachment; filename=" + resultFileName)
        .build();
  }
}
