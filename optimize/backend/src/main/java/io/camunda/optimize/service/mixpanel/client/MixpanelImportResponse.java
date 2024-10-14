/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.mixpanel.client;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;

public class MixpanelImportResponse {

  @JsonProperty("error")
  private String error;

  @JsonProperty("num_records_imported")
  private int numberOfRecordsImported;

  public MixpanelImportResponse() {}

  @JsonIgnore
  public boolean isSuccessful() {
    return StringUtils.isEmpty(error);
  }

  public String getError() {
    return error;
  }

  @JsonProperty("error")
  public void setError(final String error) {
    this.error = error;
  }

  public int getNumberOfRecordsImported() {
    return numberOfRecordsImported;
  }

  @JsonProperty("num_records_imported")
  public void setNumberOfRecordsImported(final int numberOfRecordsImported) {
    this.numberOfRecordsImported = numberOfRecordsImported;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof MixpanelImportResponse;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $error = getError();
    result = result * PRIME + ($error == null ? 43 : $error.hashCode());
    result = result * PRIME + getNumberOfRecordsImported();
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof MixpanelImportResponse)) {
      return false;
    }
    final MixpanelImportResponse other = (MixpanelImportResponse) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$error = getError();
    final Object other$error = other.getError();
    if (this$error == null ? other$error != null : !this$error.equals(other$error)) {
      return false;
    }
    if (getNumberOfRecordsImported() != other.getNumberOfRecordsImported()) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "MixpanelImportResponse(error="
        + getError()
        + ", numberOfRecordsImported="
        + getNumberOfRecordsImported()
        + ")";
  }
}
