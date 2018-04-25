package org.camunda.optimize.dto.optimize.query.user;

import java.util.ArrayList;
import java.util.List;

public class ProcessDefinitionPermissionsDto {

  private boolean useWhiteList = false;
  private List<String> idList = new ArrayList<>();

  public boolean isUseWhiteList() {
    return useWhiteList;
  }

  public void setUseWhiteList(boolean useWhiteList) {
    this.useWhiteList = useWhiteList;
  }

  public List<String> getIdList() {
    return idList;
  }

  public void setIdList(List<String> idList) {
    this.idList = idList;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof ProcessDefinitionPermissionsDto) {
      ProcessDefinitionPermissionsDto other = (ProcessDefinitionPermissionsDto) o;
      return other.useWhiteList == useWhiteList &&
        other.idList != null &&
        other.idList.size() == idList.size() &&
        other.idList.containsAll(idList);
    }
    return false;
  }
}
