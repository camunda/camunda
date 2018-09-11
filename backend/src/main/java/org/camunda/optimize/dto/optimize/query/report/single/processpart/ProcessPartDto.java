package org.camunda.optimize.dto.optimize.query.report.single.processpart;

import org.camunda.optimize.dto.optimize.query.report.Combinable;

import java.util.Objects;

public class ProcessPartDto implements Combinable {

  protected String start;
  protected String end;

  public String getStart() {
    return start;
  }

  public void setStart(String start) {
    this.start = start;
  }

  public String getEnd() {
    return end;
  }

  public void setEnd(String end) {
    this.end = end;
  }

  @Override
  public boolean isCombinable(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ProcessPartDto)) {
      return false;
    }
    ProcessPartDto that = (ProcessPartDto) o;
    return Objects.equals(start, that.start) &&
      Objects.equals(end, that.end);
  }

  public String createCommandKey() {
    return "processPart";
  }
}
