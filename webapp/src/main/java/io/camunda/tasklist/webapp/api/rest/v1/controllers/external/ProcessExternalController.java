/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers.external;

import static java.util.Objects.requireNonNullElse;

import io.camunda.tasklist.entities.ProcessEntity;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.store.FormStore;
import io.camunda.tasklist.store.ProcessStore;
import io.camunda.tasklist.webapp.api.rest.v1.controllers.ApiErrorController;
import io.camunda.tasklist.webapp.api.rest.v1.entities.FormResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.StartProcessRequest;
import io.camunda.tasklist.webapp.graphql.entity.ProcessInstanceDTO;
import io.camunda.tasklist.webapp.rest.exception.Error;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.tasklist.webapp.security.tenant.TenantService;
import io.camunda.tasklist.webapp.service.ProcessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "External Process", description = "API to manage processes by external consumers.")
@RestController
@ConditionalOnProperty(
    value = "camunda.tasklist.featureFlag.processPublicEndpoints",
    matchIfMissing = true)
@RequestMapping(
    value = TasklistURIs.EXTERNAL_PROCESS_URL_V1,
    produces = MediaType.APPLICATION_JSON_VALUE)
public class ProcessExternalController extends ApiErrorController {

  @Autowired private ProcessStore processStore;

  @Autowired private ProcessService processService;

  @Autowired private FormStore formStore;

  @Autowired private TenantService tenantService;

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
      final ProcessEntity process = processStore.getProcessByBpmnProcessId(bpmnProcessId);
      if (!process.isStartedByForm()) {
        throw new NotFoundApiException(
            String.format("The process with bpmnProcessId: '%s' is not found", bpmnProcessId));
      } else {
        final String formId = StringUtils.substringAfterLast(process.getFormKey(), ":");
        final var form = formStore.getForm(formId, process.getId());
        return ResponseEntity.ok(FormResponse.fromFormEntity(form, process));
      }
    } catch (TasklistRuntimeException e) {
      throw new NotFoundApiException("Not found");
    }
  }

  @PatchMapping("{bpmnProcessId}/start")
  public ResponseEntity<ProcessInstanceDTO> startProcess(
      @PathVariable String bpmnProcessId,
      @RequestParam(required = false) String tenantId,
      @RequestBody(required = false) StartProcessRequest startProcessRequest) {

    if (tenantService.isMultiTenancyEnabled()) {
      if (StringUtils.isBlank(tenantId)
          || !tenantService.getAuthenticatedTenants().contains(tenantId)) {
        throw new InvalidRequestException("Invalid Tenant");
      }
    }

    final ProcessEntity process = processStore.getProcessByBpmnProcessId(bpmnProcessId, tenantId);
    if (!process.isStartedByForm()) {
      throw new NotFoundApiException(
          String.format("The process with processDefinitionKey: '%s' is not found", bpmnProcessId));
    } else {
      final var variables =
          requireNonNullElse(startProcessRequest, new StartProcessRequest()).getVariables();
      final ProcessInstanceDTO processInstanceDTO =
          processService.startProcessInstance(bpmnProcessId, variables, tenantId);
      return ResponseEntity.ok(processInstanceDTO);
    }
  }
}
