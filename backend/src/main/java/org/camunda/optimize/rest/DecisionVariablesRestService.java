/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.es.reader.DecisionVariableReader;
import org.camunda.optimize.service.util.ValidationHelper;
import org.glassfish.jersey.internal.util.collection.MultivaluedStringMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.rest.queryparam.adjustment.QueryParamAdjustmentUtil.adjustVariableValuesToQueryParameters;

@Path("/decision-variables")
@Secured
@Component
public class DecisionVariablesRestService {

  public static final String VARIABLE_ID = "variableId";
  public static final String VARIABLE_TYPE = "variableType";
  public static final String DECISION_DEFINITION_KEY = "decisionDefinitionKey";
  public static final String DECISION_DEFINITION_VERSION = "decisionDefinitionVersion";
  public static final String NUM_RESULTS = "numResults";
  public static final String RESULT_OFFSET = "resultOffset";
  public static final String VALUE_FILTER = "valueFilter";

  @Autowired
  private DecisionVariableReader decisionVariableReader;

  @GET
  @Path("/inputs/values")
  @Produces(MediaType.APPLICATION_JSON)
  public List<String> getInputValues(
      @QueryParam(DECISION_DEFINITION_KEY) final String decisionDefinitionKey,
      @QueryParam(DECISION_DEFINITION_VERSION) final String decisionDefinitionVersion,
      @QueryParam(VARIABLE_ID) final String variableId,
      @QueryParam(VARIABLE_TYPE) final String variableType,
      @QueryParam(VALUE_FILTER) final String valueFilter,
      @QueryParam(RESULT_OFFSET) final String resultOffset,
      @QueryParam(NUM_RESULTS) final String numResults) {
    ValidationHelper.ensureNotEmpty("decision definition key", decisionDefinitionKey);
    ValidationHelper.ensureNotEmpty("decision definition version", decisionDefinitionVersion);
    ValidationHelper.ensureNotEmpty("input variable id", variableId);
    ValidationHelper.ensureNotEmpty("input variable type", variableType);

    final VariableType strictVariableType = VariableType.getTypeForId(variableType);
    List<String> variableValues = decisionVariableReader.getInputVariableValues(
      decisionDefinitionKey, decisionDefinitionVersion, variableId, strictVariableType, valueFilter
    );

    return applyOffsetAndLimit(resultOffset, numResults, variableValues);
  }

  @GET
  @Path("/outputs/values")
  @Produces(MediaType.APPLICATION_JSON)
  public List<String> getOutputValues(
    @QueryParam(DECISION_DEFINITION_KEY) final String decisionDefinitionKey,
    @QueryParam(DECISION_DEFINITION_VERSION) final String decisionDefinitionVersion,
    @QueryParam(VARIABLE_ID) final String variableId,
    @QueryParam(VARIABLE_TYPE) final String variableType,
    @QueryParam(VALUE_FILTER) final String valueFilter,
    @QueryParam(RESULT_OFFSET) final String resultOffset,
    @QueryParam(NUM_RESULTS) final String numResults) {
    ValidationHelper.ensureNotEmpty("decision definition key", decisionDefinitionKey);
    ValidationHelper.ensureNotEmpty("decision definition version", decisionDefinitionVersion);
    ValidationHelper.ensureNotEmpty("output variable id", variableId);
    ValidationHelper.ensureNotEmpty("output variable type", variableType);

    final VariableType strictVariableType = VariableType.getTypeForId(variableType);
    List<String> variableValues =  decisionVariableReader.getOutputVariableValues(
      decisionDefinitionKey, decisionDefinitionVersion, variableId, strictVariableType, valueFilter
    );

    return applyOffsetAndLimit(resultOffset, numResults, variableValues);
  }

  public List<String> applyOffsetAndLimit(final String resultOffset,
                                          final String numResults,
                                          final List<String> variableValues) {
    final MultivaluedMap<String, String> queryParameters = new MultivaluedStringMap();
    queryParameters.put(RESULT_OFFSET, Collections.singletonList(resultOffset));
    queryParameters.put(NUM_RESULTS, Collections.singletonList(numResults));
    return adjustVariableValuesToQueryParameters(variableValues, queryParameters);
  }

}
