/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util;

import com.github.sisyphsu.dateparser.DateParserUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DateFormatterUtil {

  private static final DateTimeFormatter OPTIMIZE_FORMATTER = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);

  public static boolean isValidOptimizeDateFormat(final String value) {
    try {
      OffsetDateTime.parse(value, OPTIMIZE_FORMATTER);
      return true;
    } catch (DateTimeParseException ex) {
      return false;
    }
  }

  public static Optional<String> getDateStringInOptimizeDateFormat(final String dateString) {
    try {
      final OffsetDateTime parsedOffsetDateTime = DateParserUtils.parseOffsetDateTime(dateString);
      return Optional.of(parsedOffsetDateTime.format(OPTIMIZE_FORMATTER));
    } catch (DateTimeParseException ex) {
      return Optional.empty();
    }
  }

}
