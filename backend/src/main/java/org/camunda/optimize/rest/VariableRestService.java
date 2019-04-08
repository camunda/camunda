/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.variable.VariableRetrievalDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.es.reader.VariableReader;
import org.camunda.optimize.service.util.ValidationHelper;
import org.glassfish.jersey.internal.util.collection.MultivaluedStringMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.rest.queryparam.adjustment.QueryParamAdjustmentUtil.adjustVariableValuesToQueryParameters;
import static org.camunda.optimize.rest.queryparam.adjustment.QueryParamAdjustmentUtil.adjustVariablesToQueryParameters;

@Path("/variables")
@Secured
@Component
public class VariableRestService {

  public static final String NAME = "name";
  public static final String TYPE = "type";
  public static final String PROCESS_DEFINITION_KEY = "processDefinitionKey";
  public static final String PROCESS_DEFINITION_VERSION = "processDefinitionVersion";
  public static final String NAME_PREFIX = "namePrefix";
  public static final String SORT_ORDER = "sortOrder";
  public static final String NUM_RESULTS = "numResults";
  public static final String RESULT_OFFSET = "resultOffset";
  public static final String ORDER_BY = "orderBy";
  public static final String VALUE_FILTER = "valueFilter";

  @Autowired
  private VariableReader variableReader;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<VariableRetrievalDto> getVariables(
      @QueryParam(PROCESS_DEFINITION_KEY) String processDefinitionKey,
      @QueryParam(PROCESS_DEFINITION_VERSION) String processDefinitionVersion,
      @QueryParam(NAME_PREFIX) String namePrefix,
      @QueryParam(ORDER_BY) @DefaultValue("name") String orderBy,
      @QueryParam(SORT_ORDER) @DefaultValue("asc") String sortOrder) {

    MultivaluedMap<String, String> queryParameters = new MultivaluedStringMap();

    queryParameters.put(SORT_ORDER, Collections.singletonList(sortOrder));
    queryParameters.put(ORDER_BY, Collections.singletonList(orderBy));

    ValidationHelper.ensureNotEmpty("process definition key", processDefinitionKey);
    ValidationHelper.ensureNotEmpty("process definition version", processDefinitionVersion);
    List<VariableRetrievalDto> variables = variableReader.getVariables(processDefinitionKey, processDefinitionVersion, namePrefix);
    return adjustVariablesToQueryParameters(variables, queryParameters);
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
      @QueryParam(VALUE_FILTER) String valueFilter,
      @QueryParam(RESULT_OFFSET) String resultOffset,
      @QueryParam(NUM_RESULTS) String numResults) {

    ValidationHelper.ensureNotEmpty("process definition key", processDefinitionKey);
    ValidationHelper.ensureNotEmpty("process definition version", processDefinitionVersion);
    ValidationHelper.ensureNotEmpty("variable name", name);
    ValidationHelper.ensureNotEmpty("variable type", type);
    List<String> variableValues =
      variableReader.getVariableValues(processDefinitionKey, processDefinitionVersion, name, type, valueFilter);

    MultivaluedMap<String, String> queryParameters = new MultivaluedStringMap();
    queryParameters.put(RESULT_OFFSET, Collections.singletonList(resultOffset));
    queryParameters.put(NUM_RESULTS, Collections.singletonList(numResults));
    variableValues = adjustVariableValuesToQueryParameters(variableValues, queryParameters);
    return variableValues;
  }


}
