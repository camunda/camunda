/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.api.v1.rest;

import static io.camunda.operate.webapp.api.v1.rest.ProcessDefinitionController.URI;

import io.camunda.operate.webapp.api.v1.dao.ProcessDefinitionDao;
import io.camunda.operate.webapp.api.v1.entities.Error;
import io.camunda.operate.webapp.api.v1.entities.ProcessDefinition;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.ClientException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController("ProcessDefinitionControllerV1")
@RequestMapping(URI)
@Tag(name = "ProcessDefinition", description = "Process definition API")
@Validated
public class ProcessDefinitionController {

  public static final String URI = "/v1/process-definitions";
  public static final String LIST = "/list";
  public static final String BY_KEY = "/byKey";
  public static final String XML_BY_KEY = "/xmlByKey";

  private static final Logger logger = LoggerFactory.getLogger(ProcessDefinitionController.class);

  @Autowired
  private ProcessDefinitionDao processDefinitionDao;

  @Operation(
      summary = "List all process definitions",
      tags = {"Process"},
      responses = {
          @ApiResponse(
              description = "Success",
              responseCode = "200"
          ),
          @ApiResponse(
              description = ServerException.TYPE,
              responseCode = "500",
              content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Error.class))
          ),
          @ApiResponse(
              description = ClientException.TYPE,
              responseCode = "400",
              content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Error.class))
          )
      })
  @ResponseStatus(HttpStatus.OK)
  @PostMapping(value = LIST,
      consumes = {MediaType.APPLICATION_JSON_VALUE},
      produces = {MediaType.APPLICATION_JSON_VALUE}
  )
  public Results<ProcessDefinition> list(@RequestBody final Query<ProcessDefinition> query) {
    logger.debug("list {}", query);
    return processDefinitionDao.listBy(query);
  }

  @Operation(
      summary = "Get process definition by key",
      tags = {"Process"},
      responses = {
          @ApiResponse(
              description = "Success",
              responseCode = "200"
          ),
          @ApiResponse(
              description = ServerException.TYPE,
              responseCode = "500",
              content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Error.class))
          ),
          @ApiResponse(
              description = ClientException.TYPE,
              responseCode = "400",
              content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Error.class))
          ),
          @ApiResponse(
              description = ResourceNotFoundException.TYPE,
              responseCode = "404",
              content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Error.class))
          )
      })
  @ResponseStatus(HttpStatus.OK)
  @GetMapping(value = BY_KEY + "/{key}",
      produces = {MediaType.APPLICATION_JSON_VALUE}
  )
  public ProcessDefinition byKey(@PathVariable final Long key) {
    return processDefinitionDao.byKey(key);
  }

  @Operation(
      summary = "Get process definition as XML by key",
      tags = {"Process"},
      responses = {
          @ApiResponse(
              description = "Success",
              responseCode = "200"
          ),
          @ApiResponse(
              description = ServerException.TYPE,
              responseCode = "500",
              content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Error.class))
          ),
          @ApiResponse(
              description = ClientException.TYPE,
              responseCode = "400",
              content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Error.class))
          ),
          @ApiResponse(
              description = ResourceNotFoundException.TYPE,
              responseCode = "404",
              content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Error.class))
          )
      })
  @ResponseStatus(HttpStatus.OK)
  @GetMapping(value = XML_BY_KEY + "/{key}", produces = {MediaType.TEXT_XML_VALUE})
  public String xmlByKey(@Valid @PathVariable final Long key) {
    return processDefinitionDao.xmlByKey(key);
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(ClientException.class)
  public ResponseEntity<Error> handleInvalidRequest(ClientException exception) {
    final Error error = new Error()
        .setType(ResourceNotFoundException.TYPE)
        .setInstance(exception.getInstance())
        .setStatus(HttpStatus.BAD_REQUEST.value())
        .setMessage(exception.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(error);
  }

  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<Error> handleNotFound(ResourceNotFoundException exception) {
    final Error error = new Error()
        .setType(ResourceNotFoundException.TYPE)
        .setInstance(exception.getInstance())
        .setStatus(HttpStatus.NOT_FOUND.value())
        .setMessage(exception.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(error);
  }

  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  @ExceptionHandler(ServerException.class)
  public ResponseEntity<Error> handleServerException(ServerException exception) {
    final Error error = new Error()
        .setType(ServerException.TYPE)
        .setInstance(exception.getInstance())
        .setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
        .setMessage(exception.getMessage());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(error);
  }
}
