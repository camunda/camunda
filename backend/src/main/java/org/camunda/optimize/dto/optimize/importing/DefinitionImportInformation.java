package org.camunda.optimize.dto.optimize.importing;

public class DefinitionImportInformation {
  protected int definitionBasedImportIndex;
  protected String processDefinitionId;

  public int getDefinitionBasedImportIndex() {
    return definitionBasedImportIndex;
  }

  public void setDefinitionBasedImportIndex(int definitionBasedImportIndex) {
    this.definitionBasedImportIndex = definitionBasedImportIndex;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
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
