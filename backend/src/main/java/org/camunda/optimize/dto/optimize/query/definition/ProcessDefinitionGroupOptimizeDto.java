package org.camunda.optimize.dto.optimize.query.definition;

import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ProcessDefinitionGroupOptimizeDto extends KeyProcessDefinitionOptimizeDto implements Serializable, OptimizeDto {

  protected List<ExtendedProcessDefinitionOptimizeDto> versions = new ArrayList<>();

  public List<ExtendedProcessDefinitionOptimizeDto> getVersions() {
    return versions;
  }

  public void setVersions(List<ExtendedProcessDefinitionOptimizeDto> versions) {
    this.versions = versions;
  }

  public void sort() {
    versions.sort(
      Comparator.comparing(
        ExtendedProcessDefinitionOptimizeDto::getVersion)
        .reversed()
    );
  }
}
