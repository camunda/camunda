package org.camunda.optimize.dto.optimize;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ProcessDefinitionGroupOptimizeDto extends KeyProcessDefinitionOptimizeDto implements Serializable, OptimizeDto {

  protected List<ExtendedProcessDefinitionOptimizeDto> versions = new ArrayList<>();

  public List<ExtendedProcessDefinitionOptimizeDto> getVersions() {
    return versions;
  }

  public void setVersions(List<ExtendedProcessDefinitionOptimizeDto> versions) {
    this.versions = versions;
  }
}
