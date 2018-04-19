package org.camunda.optimize.service.engine.importing.index.page;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public class TimestampBasedImportPage implements ImportPage {

  private OffsetDateTime timestampOfLastEntity = OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());

  public OffsetDateTime getTimestampOfLastEntity() {
    return timestampOfLastEntity;
  }

  public void setTimestampOfLastEntity(OffsetDateTime timestampOfLastEntity) {
    this.timestampOfLastEntity = timestampOfLastEntity;
  }

}
