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
package io.camunda.operate.webapp.rest.dto.dmn;

import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.webapp.rest.dto.DtoCreator;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.*;

@Schema(
    name = "Decision group object",
    description = "Group of decisions with the same decisionId with all versions included")
public class DecisionGroupDto {

  private String decisionId;

  private String tenantId;

  private String name;

  private Set<String> permissions;

  private List<DecisionDto> decisions;

  public static List<DecisionGroupDto> createFrom(
      Map<String, List<DecisionDefinitionEntity>> decisionsGrouped) {
    return createFrom(decisionsGrouped, null);
  }

  public static List<DecisionGroupDto> createFrom(
      Map<String, List<DecisionDefinitionEntity>> decisionsGrouped,
      PermissionsService permissionsService) {
    List<DecisionGroupDto> groups = new ArrayList<>();
    decisionsGrouped.values().stream()
        .forEach(
            group -> {
              DecisionGroupDto groupDto = new DecisionGroupDto();
              DecisionDefinitionEntity decision0 = group.get(0);
              groupDto.setDecisionId(decision0.getDecisionId());
              groupDto.setTenantId(decision0.getTenantId());
              groupDto.setName(decision0.getName());
              groupDto.setPermissions(
                  permissionsService == null
                      ? new HashSet<>()
                      : permissionsService.getDecisionDefinitionPermission(
                          decision0.getDecisionId()));
              groupDto.setDecisions(DtoCreator.create(group, DecisionDto.class));
              groups.add(groupDto);
            });
    groups.sort(new DecisionGroupComparator());
    return groups;
  }

  public String getDecisionId() {
    return decisionId;
  }

  public void setDecisionId(String decisionId) {
    this.decisionId = decisionId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public DecisionGroupDto setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Set<String> getPermissions() {
    return permissions;
  }

  public void setPermissions(Set<String> permissions) {
    this.permissions = permissions;
  }

  public List<DecisionDto> getDecisions() {
    return decisions;
  }

  public void setDecisions(List<DecisionDto> decisions) {
    this.decisions = decisions;
  }

  @Override
  public int hashCode() {
    return Objects.hash(decisionId, tenantId, name, permissions, decisions);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DecisionGroupDto that = (DecisionGroupDto) o;
    return Objects.equals(decisionId, that.decisionId)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(name, that.name)
        && Objects.equals(permissions, that.permissions)
        && Objects.equals(decisions, that.decisions);
  }

  public static class DecisionGroupComparator implements Comparator<DecisionGroupDto> {
    @Override
    public int compare(DecisionGroupDto o1, DecisionGroupDto o2) {

      // when sorting "name" field has higher priority than "bpmnProcessId" field
      if (o1.getName() == null && o2.getName() == null) {
        return o1.getDecisionId().compareTo(o2.getDecisionId());
      }
      if (o1.getName() == null) {
        return 1;
      }
      if (o2.getName() == null) {
        return -1;
      }
      if (!o1.getName().equals(o2.getName())) {
        return o1.getName().compareTo(o2.getName());
      }
      return o1.getDecisionId().compareTo(o2.getDecisionId());
    }
  }
}
