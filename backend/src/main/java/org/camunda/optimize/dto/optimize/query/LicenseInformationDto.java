package org.camunda.optimize.dto.optimize.query;

import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.io.Serializable;
import java.util.Date;

public class LicenseInformationDto implements OptimizeDto, Serializable {

  private String customerId;
  private Date validUntil;
  private boolean isUnlimited;

  public String getCustomerId() {
    return customerId;
  }

  public void setCustomerId(String customerId) {
    this.customerId = customerId;
  }

  public Date getValidUntil() {
    return validUntil;
  }

  public void setValidUntil(Date validUntil) {
    this.validUntil = validUntil;
  }

  public boolean isUnlimited() {
    return isUnlimited;
  }

  public void setUnlimited(boolean unlimited) {
    isUnlimited = unlimited;
  }
}
