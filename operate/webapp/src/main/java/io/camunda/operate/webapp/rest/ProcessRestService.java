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
package io.camunda.operate.webapp.rest;

import static io.camunda.operate.webapp.rest.ProcessRestService.PROCESS_URL;

import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.util.rest.ValidLongId;
import io.camunda.operate.webapp.InternalAPIErrorController;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.reader.ProcessReader;
import io.camunda.operate.webapp.rest.dto.DtoCreator;
import io.camunda.operate.webapp.rest.dto.ProcessDto;
import io.camunda.operate.webapp.rest.dto.ProcessGroupDto;
import io.camunda.operate.webapp.rest.dto.ProcessRequestDto;
import io.camunda.operate.webapp.rest.exception.NotAuthorizedException;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Processes")
@RestController
@RequestMapping(value = PROCESS_URL)
public class ProcessRestService extends InternalAPIErrorController {

  public static final String PROCESS_URL = "/api/processes";
  @Autowired protected ProcessReader processReader;
  @Autowired protected ProcessInstanceReader processInstanceReader;

  @Autowired(required = false)
  protected PermissionsService permissionsService;

  @Autowired private BatchOperationWriter batchOperationWriter;

  @Operation(summary = "Get process BPMN XML")
  @GetMapping(path = "/{id}/xml")
  public String getProcessDiagram(@PathVariable("id") String processId) {
    final Long processDefinitionKey = Long.valueOf(processId);
    final ProcessEntity processEntity = processReader.getProcess(processDefinitionKey);
    checkIdentityReadPermission(processEntity.getBpmnProcessId());
    return processReader.getDiagram(processDefinitionKey);
  }

  @Operation(summary = "Get process by id")
  @GetMapping(path = "/{id}")
  public ProcessDto getProcess(@PathVariable("id") String processId) {
    final ProcessEntity processEntity = processReader.getProcess(Long.valueOf(processId));
    checkIdentityReadPermission(processEntity.getBpmnProcessId());
    return DtoCreator.create(processEntity, ProcessDto.class);
  }

  @Operation(summary = "List processes grouped by bpmnProcessId")
  @GetMapping(path = "/grouped")
  @Deprecated
  public List<ProcessGroupDto> getProcessesGrouped() {
    final var processesGrouped = processReader.getProcessesGrouped(new ProcessRequestDto());
    return ProcessGroupDto.createFrom(processesGrouped, permissionsService);
  }

  @Operation(summary = "List processes grouped by bpmnProcessId")
  @PostMapping(path = "/grouped")
  public List<ProcessGroupDto> getProcessesGrouped(@RequestBody ProcessRequestDto request) {
    final var processesGrouped = processReader.getProcessesGrouped(request);
    return ProcessGroupDto.createFrom(processesGrouped, permissionsService);
  }

  @Operation(summary = "Delete process definition and dependant resources")
  @DeleteMapping(path = "/{id}")
  @PreAuthorize("hasPermission('write')")
  public BatchOperationEntity deleteProcessDefinition(
      @ValidLongId @PathVariable("id") String processId) {
    final ProcessEntity processEntity = processReader.getProcess(Long.valueOf(processId));
    checkIdentityDeletePermission(processEntity.getBpmnProcessId());
    return batchOperationWriter.scheduleDeleteProcessDefinition(processEntity);
  }

  private void checkIdentityReadPermission(String bpmnProcessId) {
    if (permissionsService != null
        && !permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.READ)) {
      throw new NotAuthorizedException(
          String.format("No read permission for process %s", bpmnProcessId));
    }
  }

  private void checkIdentityDeletePermission(String bpmnProcessId) {
    if (permissionsService != null
        && !permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.DELETE)) {
      throw new NotAuthorizedException(
          String.format("No delete permission for process %s", bpmnProcessId));
    }
  }
}
