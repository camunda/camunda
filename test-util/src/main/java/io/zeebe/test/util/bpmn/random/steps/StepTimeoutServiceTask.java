package io.zeebe.test.util.bpmn.random.steps;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class StepTimeoutServiceTask extends AbstractExecutionStep {

  private final String jobType;
  private final String boundaryTimerEventId;

  public StepTimeoutServiceTask(final String jobType, final String boundaryTimerEventId) {
    this.jobType = jobType;
    this.boundaryTimerEventId = boundaryTimerEventId;
  }

  @Override
  protected Map<String, Object> updateVariables(
      final Map<String, Object> variables, final Duration activationDuration) {
    final var result = new HashMap<>(variables);
    result.put(boundaryTimerEventId, activationDuration.toString());
    return result;
  }

  @Override
  public boolean isAutomatic() {
    return false;
  }

  @Override
  public Duration getDeltaTime() {
    return DEFAULT_DELTA;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), jobType, boundaryTimerEventId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final StepTimeoutServiceTask that = (StepTimeoutServiceTask) o;
    return jobType.equals(that.jobType) && boundaryTimerEventId.equals(that.boundaryTimerEventId);
  }

  public String getBoundaryTimerEventId() {
    return boundaryTimerEventId;
  }
}
