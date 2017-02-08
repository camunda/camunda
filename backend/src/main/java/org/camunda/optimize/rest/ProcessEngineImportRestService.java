package org.camunda.optimize.rest;

import org.camunda.optimize.service.importing.ImportServiceHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

@Path("/import")
@Component
public class ProcessEngineImportRestService {

  @Autowired
  private ImportServiceHandler importServiceHandler;

  @GET
  public Response importDataFromEngine() {
    importServiceHandler.executeProcessEngineImport();
    return Response.ok().build();
  }

}
