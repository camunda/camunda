/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.model.bpmn.util.time;

public class ExpressionTimer implements Timer {

  private String timerExpression;
  private Interval interval;

  public ExpressionTimer(String timerExpression) {
    this.timerExpression = timerExpression;
  }

  @Override
  public Interval getInterval() {
    return interval;
  }

  public void setInterval(Interval interval) {
    this.interval = interval;
  }

  @Override
  public int getRepetitions() {
    return 1;
  }

  @Override
  public long getDueDate(final long fromEpochMillis) {
    return getInterval().toEpochMilli(fromEpochMillis);
  }

  public String getTimerExpression() {
    return timerExpression;
  }
}
