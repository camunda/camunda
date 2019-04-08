/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.ProcessDefinitionGroupOptimizeDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.security.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.List;


@Secured
@Path("/process-definition")
@Component
public class ProcessDefinitionRestService {
  private static final Logger logger = LoggerFactory.getLogger(ProcessDefinitionRestService.class);

  @Autowired
  private ProcessDefinitionReader processDefinitionReader;

  @Autowired
  private SessionService sessionService;

  /**
   * Retrieves all process definition stored in Optimize.
   *
   * @param includeXml A parameter saying if the process definition xml should be included to the response.
   * @return A collection of process definitions.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Collection<ProcessDefinitionOptimizeDto> getProcessDefinitions(
    @Context ContainerRequestContext requestContext,
    @QueryParam("includeXml") boolean includeXml) {

    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return processDefinitionReader.fetchFullyImportedProcessDefinitions(userId, includeXml);
  }

  /**
   * Retrieves all process definition stored in Optimize and groups them by key.
   *
   * @return A collection of process definitions.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/groupedByKey")
  public List<ProcessDefinitionGroupOptimizeDto> getProcessDefinitionsGroupedByKey(
    @Context ContainerRequestContext requestContext) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return processDefinitionReader.getProcessDefinitionsGroupedByKey(userId);
  }

  /**
   * Get the process definition xml to a given process definition key and version.
   * If the version is set to "ALL", the xml of the latest version is returned.
   *
   * @param processDefinitionKey     The process definition key of the desired process definition xml.
   * @param processDefinitionVersion The process definition version of the desired process definition xml.
   * @return the process definition xml requested or json error structure on failure
   */
  @GET
  @Produces(value = {MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
  @Path("/xml")
  public String getProcessDefinitionXml(
    @Context ContainerRequestContext requestContext,
    @QueryParam("processDefinitionKey") String processDefinitionKey,
    @QueryParam("processDefinitionVersion") String processDefinitionVersion) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return processDefinitionReader.getProcessDefinitionXml(userId, processDefinitionKey, processDefinitionVersion)
      .orElseThrow(() -> {
        String notFoundErrorMessage = "Could not find xml for process definition with key [" + processDefinitionKey +
          "] and version [" + processDefinitionVersion + "]. It is possible that is hasn't been imported yet.";
        logger.error(notFoundErrorMessage);
        return new NotFoundException(notFoundErrorMessage);
      });
  }
}
