package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.variable.VariableRetrievalDto;
import org.camunda.optimize.service.es.reader.VariableReader;
import org.camunda.optimize.service.util.ValidationHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import java.util.List;

import static org.camunda.optimize.rest.queryparam.adjustment.QueryParamAdjustmentUtil.adjustVariableValuesToQueryParameters;

@Path("/variables")
@Component
public class VariableRestService {

  public static final String NAME = "name";
  public static final String TYPE = "type";

  @Autowired
  private VariableReader variableReader;

  @GET
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<VariableRetrievalDto> getVariables(@PathParam("id") String processDefinitionId,
                                                 @Context UriInfo uriInfo) {
    return variableReader.getVariables(processDefinitionId);
  }

  @GET
  @Path("/{id}/values")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<String> getVariableValues(@PathParam("id") String processDefinitionId,
                                        @Context UriInfo uriInfo) {
    MultivaluedMap<String, String> queryParameters = uriInfo.getQueryParameters();
    String name = queryParameters.getFirst(NAME);
    ValidationHelper.ensureNotEmpty("variable name", name);
    String type = queryParameters.getFirst(TYPE);
    ValidationHelper.ensureNotEmpty("variable type", type);
    List<String> variableValues = variableReader.getVariableValues(processDefinitionId, name, type);
    variableValues = adjustVariableValuesToQueryParameters(variableValues, queryParameters);
    return variableValues;
  }


}
