package org.camunda.optimize.dto.optimize;

import java.util.List;

/**
 * @author Askar Akhmerov
 */
public class FilterDto {
  protected List<DateDto> dates;

  public List<DateDto> getDates() {
    return dates;
  }

  public void setDates(List<DateDto> dates) {
    this.dates = dates;
  }
}
