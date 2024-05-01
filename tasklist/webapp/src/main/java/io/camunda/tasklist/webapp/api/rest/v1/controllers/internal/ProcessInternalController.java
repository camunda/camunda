/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers.internal;

import static java.util.Objects.requireNonNullElse;

import io.camunda.tasklist.entities.ProcessEntity;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.store.FormStore;
import io.camunda.tasklist.store.ProcessInstanceStore;
import io.camunda.tasklist.store.ProcessStore;
import io.camunda.tasklist.webapp.api.rest.v1.controllers.ApiErrorController;
import io.camunda.tasklist.webapp.api.rest.v1.entities.ProcessPublicEndpointsResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.ProcessResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.StartProcessRequest;
import io.camunda.tasklist.webapp.graphql.entity.ProcessInstanceDTO;
import io.camunda.tasklist.webapp.rest.exception.Error;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.tasklist.webapp.security.identity.IdentityAuthorizationService;
import io.camunda.tasklist.webapp.security.tenant.TenantService;
import io.camunda.tasklist.webapp.service.ProcessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Process", description = "API to manage processes.")
@RestController
@RequestMapping(value = TasklistURIs.PROCESSES_URL_V1, produces = MediaType.APPLICATION_JSON_VALUE)
public class ProcessInternalController extends ApiErrorController {

  @Autowired private ProcessStore processStore;
  @Autowired private FormStore formStore;
  @Autowired private ProcessService processService;
  @Autowired private ProcessInstanceStore processInstanceStore;
  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private IdentityAuthorizationService identityAuthorizationService;
  @Autowired private TenantService tenantService;

  @Operation(
      summary = "Returns the process by ProcessDefinitionKey",
      description = "Returns the process by ProcessDefinitionKey",
      responses = {
        @ApiResponse(
            description = "On success returned",
            responseCode = "200",
            useReturnTypeSchema = true),
        @ApiResponse(description = "Process Not Found", responseCode = "404")
      })
  @GetMapping("{processDefinitionKey}")
  public ResponseEntity<ProcessResponse> getProcess(
      @PathVariable final String processDefinitionKey) {
    final ProcessEntity processEntity =
        processService.getProcessByProcessDefinitionKeyAndAccessRestriction(processDefinitionKey);
    final ProcessResponse processResponse =
        new ProcessResponse()
            .fromProcessEntity(processEntity, getStartEventFormIdByBpmnProcess(processEntity));
    return ResponseEntity.ok(processResponse);
  }

  @Operation(
      summary = "Returns the list of processes by search query",
      description = "Get the processes by `search` query.",
      responses = {
        @ApiResponse(
            description = "On success returned",
            responseCode = "200",
            useReturnTypeSchema = true)
      })
  @GetMapping
  public ResponseEntity<List<ProcessResponse>> searchProcesses(
      @Parameter(
              description =
                  "Used to search processes by processId, process name, and process definition id fields.")
          @RequestParam(defaultValue = StringUtils.EMPTY)
          final String query,
      @Parameter(
              description =
                  "Identifies the tenant.<br>"
                      + "If multi-tenancy is enabled and `tenantId` is not provided, processes for all tenants available for the current user will be returned.<br>"
                      + "If `tenantId` is provided, only processes for that tenant will be returned, or an empty list if the user does not have access to the provided tenant.<br>"
                      + "If multi-tenancy is disabled, this parameter will be ignored.")
          @RequestParam(required = false)
          final String tenantId,
      @Parameter(
              description =
                  "If this parameter is set (Default value `null`): <br>"
                      + "`true`: It will return all the processes started by a form <br>"
                      + "`false`: It will return all the processes that are not started by a form <br>"
                      + "`null`: The filter is not applied")
          @RequestParam(required = false)
          final Boolean isStartedByForm) {

    final var processes =
        processStore
            .getProcesses(
                query,
                identityAuthorizationService.getProcessDefinitionsFromAuthorization(),
                tenantId,
                isStartedByForm)
            .stream()
            .map(
                pe ->
                    ProcessResponse.fromProcessEntityWithoutBpmnXml(
                        pe, getStartEventFormIdByBpmnProcess(pe)))
            .collect(Collectors.toList());
    return ResponseEntity.ok(processes);
  }

  /** Retrieving the start event form id when exists. */
  private String getStartEventFormIdByBpmnProcess(final ProcessEntity process) {
    if (process.getIsFormEmbedded() != null && !process.getIsFormEmbedded()) {
      if (process.getFormId() != null) {
        try {
          final var form = formStore.getForm(process.getFormId(), process.getId(), null);
          return form.getBpmnId();
        } catch (final NotFoundException e) {
          // Form not found, but maintain the Form ID in order to threat not found in front-end
          return process.getFormId();
        }
      }

    } else {
      if (process.getFormKey() != null) {
        final String formId = StringUtils.substringAfterLast(process.getFormKey(), ":");
        final var form = formStore.getForm(formId, process.getId(), null);
        return form.getBpmnId();
      }
    }
    return null;
  }

  @Operation(
      summary = "Start process by bpmnProcessId and tenantId when multi-tenancy is active",
      description = "Start process by `bpmnProcessId` and `tenantId` when multi-tenancy is active.",
      responses = {
        @ApiResponse(
            description = "On success returned",
            responseCode = "200",
            useReturnTypeSchema = true),
        @ApiResponse(
            description =
                "An error is returned when invalid or missing `tenantId` provided when multi-tenancy is active.",
            responseCode = "400",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class))),
        @ApiResponse(
            description = "An error is returned when the process is not found by `bpmnProcessId`.",
            responseCode = "404",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class)))
      })
  @PreAuthorize("hasPermission('write')")
  @PatchMapping("{bpmnProcessId}/start")
  public ResponseEntity<ProcessInstanceDTO> startProcessInstance(
      @PathVariable final String bpmnProcessId,
      @Parameter(
              description =
                  "Required for multi-tenancy setups to ensure the process starts for the intended tenant. In environments without multi-tenancy, this parameter is not considered.")
          @RequestParam(required = false)
          final String tenantId,
      @RequestBody(required = false) final StartProcessRequest startProcessRequest) {
    final var variables =
        requireNonNullElse(startProcessRequest, new StartProcessRequest()).getVariables();
    final var processInstance =
        processService.startProcessInstance(bpmnProcessId, variables, tenantId);
    return ResponseEntity.ok(processInstance);
  }

  @Operation(
      summary = "Delete process instance by given processInstanceId.",
      description = "Delete process instance by given `processInstanceId`.",
      responses = {
        @ApiResponse(
            description = "On success returned",
            responseCode = "204",
            useReturnTypeSchema = true),
        @ApiResponse(
            description =
                "An error is returned when the `processInstance` with `processInstanceId` is not found`.",
            responseCode = "404",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class))),
        @ApiResponse(
            description =
                "An error is returned when the `processInstance` with `processInstanceId` could not be deleted`.",
            responseCode = "500",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class)))
      })
  @PreAuthorize("hasPermission('write')")
  @DeleteMapping("{processInstanceId}")
  public ResponseEntity<?> deleteProcessInstance(@PathVariable final String processInstanceId) {

    return switch (processInstanceStore.deleteProcessInstance(processInstanceId)) {
      case DELETED -> ResponseEntity.noContent().build();
      case NOT_FOUND ->
          throw new NotFoundApiException(
              String.format(
                  "The process with processInstanceId: '%s' is not found", processInstanceId));
      default ->
          throw new TasklistRuntimeException(
              String.format(
                  "The deletion of process with processInstanceId: '%s' could not be deleted",
                  processInstanceId));
    };
  }

  @Operation(
      summary = "Return all the public endpoints to start a process by a form.",
      description = "Return all the public endpoints to start a process by a form.",
      responses = {
        @ApiResponse(
            description = "On success returned",
            responseCode = "200",
            useReturnTypeSchema = true),
      })
  @GetMapping("publicEndpoints")
  public ResponseEntity<List<ProcessPublicEndpointsResponse>> getPublicEndpoints() {

    final List<ProcessPublicEndpointsResponse> publicEndpoints;

    if (tasklistProperties.getFeatureFlag().getProcessPublicEndpoints()) {
      publicEndpoints =
          processStore.getProcessesStartedByForm().stream()
              .map(ProcessPublicEndpointsResponse::fromProcessEntity)
              .collect(Collectors.toList());
    } else {
      publicEndpoints = Collections.emptyList();
    }

    return ResponseEntity.ok(publicEndpoints);
  }

  @Operation(
      summary = "Fetch public endpoint to initiate process via a form.",
      description =
          "Provides a public endpoint for starting a process using a form, based on the given `processDefinitionKey`. Ensure the correct `tenantId` is provided in multi-tenancy setups.",
      responses = {
        @ApiResponse(
            description = "On success returned",
            responseCode = "200",
            useReturnTypeSchema = true),
        @ApiResponse(
            description =
                "An error is returned when invalid or missing `tenantId` provided when multi-tenancy is active.",
            responseCode = "400",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class))),
        @ApiResponse(
            description =
                "An error is returned when the public endpoint is not found by `processDefinitionKey`.",
            responseCode = "404",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = Error.class)))
      })
  @GetMapping("{bpmnProcessId}/publicEndpoint")
  public ResponseEntity<ProcessPublicEndpointsResponse> getPublicEndpoint(
      @PathVariable final String bpmnProcessId,
      @Parameter(
              description =
                  "If using multi-tenancy, this parameter ensures the system fetches the public endpoint for the correct tenant. In environments without multi-tenancy, this parameter is not considered.")
          @RequestParam(required = false)
          final String tenantId) {

    if (!tenantService.isTenantValid(tenantId)) {
      throw new InvalidRequestException("Invalid Tenant");
    }

    final var process = processStore.getProcessByBpmnProcessId(bpmnProcessId, tenantId);
    if (!process.isStartedByForm()) {
      throw new NotFoundApiException(
          String.format("The public endpoint for bpmnProcessId: '%s' is not found", bpmnProcessId));
    }
    return ResponseEntity.ok(ProcessPublicEndpointsResponse.fromProcessEntity(process));
  }
}
