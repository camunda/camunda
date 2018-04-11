package org.camunda.optimize.dto.optimize.importing.index;

public class AllEntitiesBasedImportIndexDto implements ImportIndexDto {

  private long importIndex;
  private String esTypeIndexRefersTo;
  private String engine;

  public String getEngine() {
    return engine;
  }

  public void setEngine(String engine) {
    this.engine = engine;
  }

  public long getImportIndex() {
    return importIndex;
  }

  public void setImportIndex(long importIndex) {
    this.importIndex = importIndex;
  }

  public String getEsTypeIndexRefersTo() {
    return esTypeIndexRefersTo;
  }

  public void setEsTypeIndexRefersTo(String esTypeIndexRefersTo) {
    this.esTypeIndexRefersTo = esTypeIndexRefersTo;
  }
}
