/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers.external;

import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.webapp.api.rest.v1.controllers.ApiErrorController;
import io.camunda.tasklist.webapp.api.rest.v1.entities.FormResponse;
import io.camunda.tasklist.webapp.es.FormReader;
import io.camunda.tasklist.webapp.es.cache.ProcessReader;
import io.camunda.tasklist.webapp.graphql.entity.ProcessDTO;
import io.camunda.tasklist.webapp.rest.exception.Error;
import io.camunda.tasklist.webapp.rest.exception.NotFoundException;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "External Process", description = "API to manage processes by external consumers.")
@RestController
@RequestMapping(
    value = TasklistURIs.EXTERNAL_PROCESS_URL_V1,
    produces = MediaType.APPLICATION_JSON_VALUE)
public class ProcessExternalController extends ApiErrorController {

  @Autowired private ProcessReader processReader;
  @Autowired private FormReader formReader;

  @Operation(
      summary = "Get Form by Process BPMN id.",
      description = "Get Form by Process BPMN id.",
      responses = {
        @ApiResponse(
            description = "On success returned",
            responseCode = "200",
            useReturnTypeSchema = true),
        @ApiResponse(
            description =
                "An error is returned when the process is not found or cannot be started by a form",
            responseCode = "404",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class)))
      })
  @GetMapping("{bpmnProcessId}/form")
  public ResponseEntity<FormResponse> getFormFromProcess(@PathVariable String bpmnProcessId) {
    try {
      final ProcessDTO process = processReader.getProcessByBpmnProcessId(bpmnProcessId);
      if (!process.isStartedByForm()) {
        throw new NotFoundException(
            String.format("The process with bpmnProcessId: '%s' is not found", bpmnProcessId));
      } else {
        final String formId = StringUtils.substringAfterLast(process.getFormKey(), ":");
        final var form = formReader.getFormDTO(formId, process.getId());
        return ResponseEntity.ok(FormResponse.fromFormDTO(form));
      }
    } catch (TasklistRuntimeException e) {
      throw new NotFoundException("Page not found");
    }
  }
}
