package org.camunda.optimize.dto.optimize.query.user;

public class PermissionsDto {

  private ProcessDefinitionPermissionsDto processDefinitions = new ProcessDefinitionPermissionsDto();
  private boolean readOnly = false;
  private boolean canSharePublicly = true;
  private boolean hasAdminRights = false;

  public ProcessDefinitionPermissionsDto getProcessDefinitions() {
    return processDefinitions;
  }

  public void setProcessDefinitions(ProcessDefinitionPermissionsDto processDefinitions) {
    this.processDefinitions = processDefinitions;
  }

  public boolean isReadOnly() {
    return readOnly;
  }

  public void setReadOnly(boolean readOnly) {
    this.readOnly = readOnly;
  }

  public boolean isHasAdminRights() {
    return hasAdminRights;
  }

  public void setHasAdminRights(boolean hasAdminRights) {
    this.hasAdminRights = hasAdminRights;
  }

  public boolean isCanSharePublicly() {
    return canSharePublicly;
  }

  public void setCanSharePublicly(boolean canSharePublicly) {
    this.canSharePublicly = canSharePublicly;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof PermissionsDto) {
      PermissionsDto other = (PermissionsDto) o;
      return other.readOnly == readOnly &&
        other.canSharePublicly == canSharePublicly &&
        other.hasAdminRights == hasAdminRights &&
        other.getProcessDefinitions() != null &&
        other.getProcessDefinitions().equals(processDefinitions);
    }
    return false;
  }
}
