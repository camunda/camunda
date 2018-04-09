package org.camunda.optimize.service.engine.importing.index.page;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public class DefinitionBasedImportPage implements ImportPage {

  private OffsetDateTime timestampOfLastEntity = OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
  protected String processDefinitionId = "";

  public String getProcessDefinitionId() {
    return this.processDefinitionId;
  }

  public void setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public OffsetDateTime getTimestampOfLastEntity() {
    return timestampOfLastEntity;
  }

  public void setTimestampOfLastEntity(OffsetDateTime timestampOfLastEntity) {
    this.timestampOfLastEntity = timestampOfLastEntity;
  }

  public DefinitionBasedImportPage copy() {
    DefinitionBasedImportPage definitionImportInformation =
      new DefinitionBasedImportPage();
    definitionImportInformation.setTimestampOfLastEntity(timestampOfLastEntity);
    definitionImportInformation.setProcessDefinitionId(processDefinitionId);
    return definitionImportInformation;
  }

  @Override
  public boolean equals(Object o) {
    if(o instanceof DefinitionBasedImportPage) {
      DefinitionBasedImportPage otherDefinitionImportInformation = (DefinitionBasedImportPage) o;
      return processDefinitionId.equals(otherDefinitionImportInformation.getProcessDefinitionId());
    } else {
      return false;
    }
  }


}
