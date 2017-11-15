package org.camunda.optimize.dto.optimize.importing.index;

public class AllEntitiesBasedImportIndexDto implements ImportIndexDto {

  private long importIndex;
  private long maxEntityCount;
  private String esTypeIndexRefersTo;

  public long getImportIndex() {
    return importIndex;
  }

  public void setImportIndex(long importIndex) {
    this.importIndex = importIndex;
  }

  public long getMaxEntityCount() {
    return maxEntityCount;
  }

  public void setMaxEntityCount(long maxEntityCount) {
    this.maxEntityCount = maxEntityCount;
  }

  public String getEsTypeIndexRefersTo() {
    return esTypeIndexRefersTo;
  }

  public void setEsTypeIndexRefersTo(String esTypeIndexRefersTo) {
    this.esTypeIndexRefersTo = esTypeIndexRefersTo;
  }
}
