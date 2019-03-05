package org.camunda.optimize.dto.optimize.query;

import java.time.OffsetDateTime;

public class TerminatedUserSessionDto {
  private String id;
  private OffsetDateTime terminationTimestamp;

  protected TerminatedUserSessionDto() {
  }

  public TerminatedUserSessionDto(final String id) {
    this(id, OffsetDateTime.now());
  }

  public TerminatedUserSessionDto(final String id, final OffsetDateTime terminationTimestamp) {
    this.id = id;
    this.terminationTimestamp = terminationTimestamp;
  }

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public OffsetDateTime getTerminationTimestamp() {
    return terminationTimestamp;
  }

  public void setTerminationTimestamp(final OffsetDateTime terminationTimestamp) {
    this.terminationTimestamp = terminationTimestamp;
  }
}
