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

import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.util.rest.ValidLongId;
import io.camunda.operate.webapp.InternalAPIErrorController;
import io.camunda.operate.webapp.reader.DecisionReader;
import io.camunda.operate.webapp.rest.dto.DecisionRequestDto;
import io.camunda.operate.webapp.rest.dto.dmn.DecisionGroupDto;
import io.camunda.operate.webapp.rest.exception.NotAuthorizedException;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Decisions")
@RestController
@RequestMapping(value = DecisionRestService.DECISION_URL)
public class DecisionRestService extends InternalAPIErrorController {

  public static final String DECISION_URL = "/api/decisions";

  @Autowired protected DecisionReader decisionReader;

  @Autowired(required = false)
  protected PermissionsService permissionsService;

  @Autowired private BatchOperationWriter batchOperationWriter;

  @Operation(summary = "Get decision DMN XML")
  @GetMapping(path = "/{id}/xml")
  public String getDecisionDiagram(@ValidLongId @PathVariable("id") String decisionDefinitionId) {
    final Long decisionDefinitionKey = Long.valueOf(decisionDefinitionId);
    checkIdentityReadPermission(decisionDefinitionKey);
    return decisionReader.getDiagram(decisionDefinitionKey);
  }

  @Operation(summary = "List decisions grouped by decisionId")
  @GetMapping(path = "/grouped")
  @Deprecated
  public List<DecisionGroupDto> getDecisionsGrouped() {
    final Map<String, List<DecisionDefinitionEntity>> decisionsGrouped =
        decisionReader.getDecisionsGrouped(new DecisionRequestDto());
    return DecisionGroupDto.createFrom(decisionsGrouped, permissionsService);
  }

  @Operation(summary = "List decisions grouped by decisionId")
  @PostMapping(path = "/grouped")
  public List<DecisionGroupDto> getDecisionsGrouped(@RequestBody DecisionRequestDto request) {
    final Map<String, List<DecisionDefinitionEntity>> decisionsGrouped =
        decisionReader.getDecisionsGrouped(request);
    return DecisionGroupDto.createFrom(decisionsGrouped, permissionsService);
  }

  @Operation(summary = "Delete decision definition and dependant resources")
  @DeleteMapping(path = "/{id}")
  @PreAuthorize("hasPermission('write')")
  public BatchOperationEntity deleteDecisionDefinition(
      @ValidLongId @PathVariable("id") String decisionDefinitionId) {
    final DecisionDefinitionEntity decisionDefinitionEntity =
        decisionReader.getDecision(Long.valueOf(decisionDefinitionId));
    checkIdentityDeletePermission(decisionDefinitionEntity.getDecisionId());
    return batchOperationWriter.scheduleDeleteDecisionDefinition(decisionDefinitionEntity);
  }

  private void checkIdentityReadPermission(Long decisionDefinitionKey) {
    if (permissionsService != null) {
      final String decisionId = decisionReader.getDecision(decisionDefinitionKey).getDecisionId();
      if (!permissionsService.hasPermissionForDecision(decisionId, IdentityPermission.READ)) {
        throw new NotAuthorizedException(
            String.format("No read permission for decision %s", decisionId));
      }
    }
  }

  private void checkIdentityDeletePermission(String decisionId) {
    if (permissionsService != null) {
      if (!permissionsService.hasPermissionForDecision(decisionId, IdentityPermission.DELETE)) {
        throw new NotAuthorizedException(
            String.format("No delete permission for decision %s", decisionId));
      }
    }
  }
}
