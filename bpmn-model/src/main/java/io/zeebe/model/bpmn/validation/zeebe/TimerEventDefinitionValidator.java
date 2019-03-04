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
package io.zeebe.model.bpmn.validation.zeebe;

import io.zeebe.model.bpmn.instance.TimeCycle;
import io.zeebe.model.bpmn.instance.TimeDate;
import io.zeebe.model.bpmn.instance.TimeDuration;
import io.zeebe.model.bpmn.instance.TimerEventDefinition;
import io.zeebe.model.bpmn.util.time.Interval;
import io.zeebe.model.bpmn.util.time.RepeatingInterval;
import io.zeebe.model.bpmn.util.time.TimeDateTimer;
import java.time.format.DateTimeParseException;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class TimerEventDefinitionValidator implements ModelElementValidator<TimerEventDefinition> {
  public static final long MAXIMUM_TIME_IN_YEARS = 100L;

  @Override
  public Class<TimerEventDefinition> getElementType() {
    return TimerEventDefinition.class;
  }

  @Override
  public void validate(
      TimerEventDefinition element, ValidationResultCollector validationResultCollector) {
    final TimeDuration timeDuration = element.getTimeDuration();
    final TimeCycle timeCycle = element.getTimeCycle();
    final TimeDate timeDate = element.getTimeDate();
    int definitionsCount = 0;

    if (timeDate != null) {

      validateTimeDate(validationResultCollector, element.getTimeDate());
      definitionsCount++;
    }

    if (timeDuration != null) {
      validateTimeDuration(validationResultCollector, timeDuration);
      definitionsCount++;
    }

    if (timeCycle != null) {
      validateTimeCycle(validationResultCollector, timeCycle);
      definitionsCount++;
    }

    if (definitionsCount != 1) {
      validationResultCollector.addError(
          0, "Must be exactly one type of timer: timeDuration, timeDate or timeCycle");
    }
  }

  private void validateTimeDate(
      ValidationResultCollector validationResultCollector, TimeDate timeDate) {
    try {
      final TimeDateTimer timer = TimeDateTimer.parse(timeDate.getTextContent());
      final long timeToTrigger = timer.getDueDate(0);
      validateTimeLimits(timeToTrigger, validationResultCollector);
    } catch (DateTimeParseException e) {
      validationResultCollector.addError(0, "Time date is invalid");
    }
  }

  private void validateTimeCycle(
      ValidationResultCollector validationResultCollector, TimeCycle timeCycle) {
    try {
      final RepeatingInterval cycle = RepeatingInterval.parse(timeCycle.getTextContent());
      final long timeToTrigger = cycle.getDueDate(System.currentTimeMillis());
      validateTimeLimits(timeToTrigger, validationResultCollector);

    } catch (DateTimeParseException e) {
      validationResultCollector.addError(0, "Time cycle is invalid");
    }
  }

  private void validateTimeDuration(
      ValidationResultCollector validationResultCollector, TimeDuration timeDuration) {
    try {
      final Interval duration = Interval.parse(timeDuration.getTextContent());
      final long timeToTrigger = duration.toEpochMilli(System.currentTimeMillis());
      validateTimeLimits(timeToTrigger, validationResultCollector);

    } catch (DateTimeParseException e) {
      validationResultCollector.addError(0, "Time duration is invalid");
    }
  }

  private void validateTimeLimits(
      long triggerTime, ValidationResultCollector validationResultCollector) {
    final long currentTime = System.currentTimeMillis();
    final long maximumTime = currentTime + convertYearsToMillis(MAXIMUM_TIME_IN_YEARS);

    if (triggerTime > maximumTime) {
      validationResultCollector.addError(
          0,
          String.format(
              "Specified time can't be more than %d years into the future", MAXIMUM_TIME_IN_YEARS));
    } else if (triggerTime < currentTime) {
      validationResultCollector.addError(0, "Specified time can't have passed already");
    }
  }

  private static long convertYearsToMillis(long years) {
    return Math.multiplyExact(years, 365 * 24 * 60 * 60 * 1000L);
  }
}
