package org.camunda.optimize.dto.optimize.query;

import java.time.Instant;

public class TerminatedUserSessionDto {
  private String id;
  private Instant terminationTimestamp;

  protected TerminatedUserSessionDto() {
  }

  public TerminatedUserSessionDto(final String id) {
    this(id, Instant.now());
  }

  public TerminatedUserSessionDto(final String id, final Instant terminationTimestamp) {
    this.id = id;
    this.terminationTimestamp = terminationTimestamp;
  }

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public Instant getTerminationTimestamp() {
    return terminationTimestamp;
  }

  public void setTerminationTimestamp(final Instant terminationTimestamp) {
    this.terminationTimestamp = terminationTimestamp;
  }
}
