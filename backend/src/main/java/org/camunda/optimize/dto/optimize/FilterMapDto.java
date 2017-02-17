package org.camunda.optimize.dto.optimize;

import java.util.List;

/**
 * @author Askar Akhmerov
 */
public class FilterMapDto {
  protected List<DateFilterDto> dates;

  public List<DateFilterDto> getDates() {
    return dates;
  }

  public void setDates(List<DateFilterDto> dates) {
    this.dates = dates;
  }
}
