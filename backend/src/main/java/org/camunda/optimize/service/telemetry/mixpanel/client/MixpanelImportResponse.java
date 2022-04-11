/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.telemetry.mixpanel.client;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@Data
@NoArgsConstructor
public class MixpanelImportResponse {
  @JsonProperty("error")
  private String error;
  @JsonProperty("num_records_imported")
  private int numberOfRecordsImported;

  @JsonIgnore
  public boolean isSuccessful() {
    return StringUtils.isEmpty(this.error);
  }
}
