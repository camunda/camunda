package org.camunda.optimize.dto.optimize.importing;

public class DefinitionImportInformation {


  protected long definitionBasedImportIndex = 0L;
  protected long maxEntityCount = 0L;
  protected String processDefinitionId = "";

  public long getDefinitionBasedImportIndex() {
    return definitionBasedImportIndex;
  }

  public boolean reachedMaxCount() {
    return definitionBasedImportIndex >= maxEntityCount;
  }

  public void moveImportIndex(long units) {
    definitionBasedImportIndex += units;
  }

  public void setDefinitionBasedImportIndex(long definitionBasedImportIndex) {
    this.definitionBasedImportIndex = definitionBasedImportIndex;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public long getMaxEntityCount() {
    return maxEntityCount;
  }

  public void setMaxEntityCount(long maxEntityCount) {
    this.maxEntityCount = maxEntityCount;
  }

  public boolean hasValidProcessDefinitionId() {
    return processDefinitionId != null && !processDefinitionId.isEmpty();
  }

  public DefinitionImportInformation copy() {
    DefinitionImportInformation definitionImportInformation =
      new DefinitionImportInformation();
    definitionImportInformation.setDefinitionBasedImportIndex(definitionBasedImportIndex);
    definitionImportInformation.setMaxEntityCount(maxEntityCount);
    definitionImportInformation.setProcessDefinitionId(processDefinitionId);
    return definitionImportInformation;
  }

  @Override
  public boolean equals(Object o) {
    if(o instanceof DefinitionImportInformation) {
      DefinitionImportInformation otherDefinitionImportInformation = (DefinitionImportInformation) o;
      return processDefinitionId.equals(otherDefinitionImportInformation.getProcessDefinitionId());
    } else {
      return false;
    }
  }
}
