/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.util;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.camunda.optimize.service.security.util.LocalDateUtil;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.TimeZone;

import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;

/**
 * This class is a util builder that helps to freeze the date for testing, e.g
 * if you want to test that a collection has the right creation date you can set
 * you this class to freeze the time for Optimize. The production code will then use
 * this frozen date to create a new date/OffsetDateTime.
 * <p>
 * For that you can also use {@link LocalDateUtil} but with Java > 11 the OffsetDateTime
 * uses nanoseconds and the handling becomes a bit trickier. This class abstracts all this
 * away so you don't have to think the correct date handling.
 */
@AllArgsConstructor
public class DateCreationFreezer {

  public static InnerDateFreezerBuilder dateFreezer() {
    return new DateCreationFreezer()
      .createNewDateFreezerBuilder()
      .setDateToFreezeToNow();
  }

  public static InnerDateFreezerBuilder dateFreezer(final OffsetDateTime dateToFreeze) {
    return new DateCreationFreezer()
      .createNewDateFreezerBuilder()
      .dateToFreeze(dateToFreeze);
  }

  private InnerDateFreezerBuilder createNewDateFreezerBuilder() {
    return new InnerDateFreezerBuilder();
  }

  @NoArgsConstructor
  public static class InnerDateFreezerBuilder {

    private OffsetDateTime dateToFreeze;
    private String timezone = ZoneId.systemDefault().getId();
    private ChronoUnit unitToTruncateTo;

    public InnerDateFreezerBuilder setDateToFreezeToNow() {
      this.dateToFreeze = LocalDateUtil.getCurrentDateTime();
      return this;
    }

    public InnerDateFreezerBuilder dateToFreeze(final OffsetDateTime dateToFreeze) {
      this.dateToFreeze = dateToFreeze;
      return this;
    }

    public InnerDateFreezerBuilder timezone(final String timezone) {
      if (dateToFreeze != null) {
        dateToFreeze.atZoneSameInstant(ZoneId.of(timezone));
      }
      this.timezone = timezone;
      return this;
    }

    public InnerDateFreezerBuilder truncateToUnit(final ChronoUnit unitToTruncateTo) {
      this.unitToTruncateTo = unitToTruncateTo;
      return this;
    }

    public OffsetDateTime freezeDateAndReturn() {
      if (dateToFreeze == null) {
        dateToFreeze = OffsetDateTime.now(TimeZone.getTimeZone(timezone).toZoneId());
      }
      if (unitToTruncateTo != null) {
        dateToFreeze = truncateToStartOfUnit(dateToFreeze, unitToTruncateTo).toOffsetDateTime();
      }
      LocalDateUtil.setCurrentTime(dateToFreeze);
      return LocalDateUtil.getCurrentDateTime();
    }
  }

}
