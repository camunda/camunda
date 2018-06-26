package org.camunda.optimize.dto.optimize.query.definition;

import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ProcessDefinitionGroupOptimizeDto extends KeyProcessDefinitionOptimizeDto implements Serializable, OptimizeDto {

  protected List<ProcessDefinitionOptimizeDto> versions = new ArrayList<>();

  public List<ProcessDefinitionOptimizeDto> getVersions() {
    return versions;
  }

  public void setVersions(List<ProcessDefinitionOptimizeDto> versions) {
    this.versions = versions;
  }

  public void sort() {
    versions.sort(
      Comparator.comparing(
        ProcessDefinitionOptimizeDto::getVersion, Comparator.comparing(Long::valueOf)
      )
        .reversed()
    );
  }
}
