package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.variable.VariableRetrievalDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.es.reader.VariableReader;
import org.camunda.optimize.service.util.ValidationHelper;
import org.glassfish.jersey.internal.util.collection.MultivaluedStringMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.rest.queryparam.adjustment.QueryParamAdjustmentUtil.adjustVariableValuesToQueryParameters;

@Path("/variables")
@Secured
@Component
public class VariableRestService {

  public static final String NAME = "name";
  public static final String TYPE = "type";
  public static final String PROCESS_DEFINITION_KEY = "processDefinitionKey";
  public static final String PROCESS_DEFINITION_VERSION = "processDefinitionVersion";

  @Autowired
  private VariableReader variableReader;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<VariableRetrievalDto> getVariables(
      @QueryParam("processDefinitionKey") String processDefinitionKey,
      @QueryParam("processDefinitionVersion") String processDefinitionVersion) {

    ValidationHelper.ensureNotEmpty("process definition key", processDefinitionKey);
    ValidationHelper.ensureNotEmpty("process definition version", processDefinitionVersion);
    return variableReader.getVariables(processDefinitionKey, processDefinitionVersion);
  }

  @GET
  @Path("/values")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<String> getVariableValues(
      @QueryParam(PROCESS_DEFINITION_KEY) String processDefinitionKey,
      @QueryParam(PROCESS_DEFINITION_VERSION) String processDefinitionVersion,
      @QueryParam(NAME) String name,
      @QueryParam(TYPE) String type,
      @QueryParam("resultOffset") String resultOffset,
      @QueryParam("numResults") String numResults) {

    ValidationHelper.ensureNotEmpty("process definition key", processDefinitionKey);
    ValidationHelper.ensureNotEmpty("process definition version", processDefinitionVersion);
    ValidationHelper.ensureNotEmpty("variable name", name);
    ValidationHelper.ensureNotEmpty("variable type", type);
    List<String> variableValues =
      variableReader.getVariableValues(processDefinitionKey, processDefinitionVersion, name, type);

    MultivaluedMap<String, String> queryParameters = new MultivaluedStringMap();
    queryParameters.put("resultOffset", Collections.singletonList(resultOffset));
    queryParameters.put("numResults", Collections.singletonList(numResults));
    variableValues = adjustVariableValuesToQueryParameters(variableValues, queryParameters);
    return variableValues;
  }


}
