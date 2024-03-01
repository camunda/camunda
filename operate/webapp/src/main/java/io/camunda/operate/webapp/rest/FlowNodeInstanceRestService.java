/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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

import static io.camunda.operate.util.CollectionUtil.countNonNullObjects;

import io.camunda.operate.webapp.InternalAPIErrorController;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceQueryDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceRequestDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceResponseDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.camunda.operate.webapp.rest.exception.NotAuthorizedException;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Flow node instances")
@RestController
@RequestMapping(value = FlowNodeInstanceRestService.FLOW_NODE_INSTANCE_URL)
public class FlowNodeInstanceRestService extends InternalAPIErrorController {

  public static final String FLOW_NODE_INSTANCE_URL = "/api/flow-node-instances";

  @Autowired(required = false)
  protected PermissionsService permissionsService;

  @Autowired private FlowNodeInstanceReader flowNodeInstanceReader;
  @Autowired private ProcessInstanceReader processInstanceReader;

  @Operation(summary = "Query flow node instance tree. Returns map treePath <-> list of children.")
  @PostMapping
  public Map<String, FlowNodeInstanceResponseDto> queryFlowNodeInstanceTree(
      @RequestBody FlowNodeInstanceRequestDto request) {
    validateRequest(request);
    checkIdentityReadPermission(Long.parseLong(request.getQueries().get(0).getProcessInstanceId()));
    return flowNodeInstanceReader.getFlowNodeInstances(request);
  }

  private void validateRequest(final FlowNodeInstanceRequestDto request) {
    if (request.getQueries() == null || request.getQueries().size() == 0) {
      throw new InvalidRequestException(
          "At least one query must be provided when requesting for flow node instance tree.");
    }

    String processInstanceId = null;
    for (FlowNodeInstanceQueryDto query : request.getQueries()) {
      if (query == null || query.getProcessInstanceId() == null || query.getTreePath() == null) {
        throw new InvalidRequestException(
            "Process instance id and tree path must be provided when requesting for flow node instance tree.");
      }
      if (countNonNullObjects(
              query.getSearchAfter(),
              query.getSearchAfterOrEqual(),
              query.getSearchBefore(),
              query.getSearchBeforeOrEqual())
          > 1) {
        throw new InvalidRequestException(
            "Only one of [searchAfter, searchAfterOrEqual, searchBefore, searchBeforeOrEqual] must be present in request.");
      }
      if (processInstanceId == null) {
        processInstanceId = query.getProcessInstanceId();
      } else if (!Objects.equals(processInstanceId, query.getProcessInstanceId())) {
        throw new InvalidRequestException(
            "Process instance id must be the same for all the queries.");
      }
    }
  }

  private void checkIdentityReadPermission(Long processInstanceKey) {
    if (permissionsService != null
        && !permissionsService.hasPermissionForProcess(
            processInstanceReader.getProcessInstanceByKey(processInstanceKey).getBpmnProcessId(),
            IdentityPermission.READ)) {
      throw new NotAuthorizedException(
          String.format("No read permission for process instance %s", processInstanceKey));
    }
  }
}
