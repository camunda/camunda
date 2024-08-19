/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.index;

public class AllEntitiesBasedImportIndexDto implements EngineImportIndexDto {

  private long importIndex;
  private String esTypeIndexRefersTo;
  private String engine;

  public AllEntitiesBasedImportIndexDto() {}

  public long getImportIndex() {
    return importIndex;
  }

  public void setImportIndex(final long importIndex) {
    this.importIndex = importIndex;
  }

  @Override
  public String getEngine() {
    return engine;
  }

  @Override
  public String getEsTypeIndexRefersTo() {
    return esTypeIndexRefersTo;
  }

  public void setEsTypeIndexRefersTo(final String esTypeIndexRefersTo) {
    this.esTypeIndexRefersTo = esTypeIndexRefersTo;
  }

  public void setEngine(final String engine) {
    this.engine = engine;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof AllEntitiesBasedImportIndexDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final long $importIndex = getImportIndex();
    result = result * PRIME + (int) ($importIndex >>> 32 ^ $importIndex);
    final Object $esTypeIndexRefersTo = getEsTypeIndexRefersTo();
    result = result * PRIME + ($esTypeIndexRefersTo == null ? 43 : $esTypeIndexRefersTo.hashCode());
    final Object $engine = getEngine();
    result = result * PRIME + ($engine == null ? 43 : $engine.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof AllEntitiesBasedImportIndexDto)) {
      return false;
    }
    final AllEntitiesBasedImportIndexDto other = (AllEntitiesBasedImportIndexDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (getImportIndex() != other.getImportIndex()) {
      return false;
    }
    final Object this$esTypeIndexRefersTo = getEsTypeIndexRefersTo();
    final Object other$esTypeIndexRefersTo = other.getEsTypeIndexRefersTo();
    if (this$esTypeIndexRefersTo == null
        ? other$esTypeIndexRefersTo != null
        : !this$esTypeIndexRefersTo.equals(other$esTypeIndexRefersTo)) {
      return false;
    }
    final Object this$engine = getEngine();
    final Object other$engine = other.getEngine();
    if (this$engine == null ? other$engine != null : !this$engine.equals(other$engine)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "AllEntitiesBasedImportIndexDto(importIndex="
        + getImportIndex()
        + ", esTypeIndexRefersTo="
        + getEsTypeIndexRefersTo()
        + ", engine="
        + getEngine()
        + ")";
  }
}
