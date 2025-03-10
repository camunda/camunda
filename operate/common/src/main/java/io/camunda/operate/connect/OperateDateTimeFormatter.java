/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.connect;

import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.conditions.DatabaseInfo;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OperateDateTimeFormatter {
  public static final String RFC3339_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSxxx";
  public static final String DATE_FORMAT_DEFAULT = "yyyy-MM-dd'T'HH:mm:ss.SSSZZ";
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final DateTimeFormatter apiDateTimeFormatter;
  private final DateTimeFormatter generalDateTimeFormatter;
  private final String apiDateTimeFormatString;
  private final String generalDateTimeFormatString;
  private final boolean storageAndApiFormatsAreSame;

  public OperateDateTimeFormatter(
      final OperateProperties operateProperties, final DatabaseInfo databaseInfo) {
    if (databaseInfo.isOpensearchDb()) {
      generalDateTimeFormatString = operateProperties.getOpensearch().getDateFormat();
    } else {
      generalDateTimeFormatString = operateProperties.getElasticsearch().getDateFormat();
    }

    if (operateProperties.isRfc3339ApiDateFormat()) {
      logger.info(
          "rfc3339ApiDateFormat is set to true, operate API will format datetimes according to the RFC3339 spec");
      apiDateTimeFormatString = RFC3339_DATE_FORMAT;
    } else {
      logger.info(
          "rfc3339ApiDateFormat is set to false, operate API will format datetimes in the existing format");
      apiDateTimeFormatString = generalDateTimeFormatString;
    }

    storageAndApiFormatsAreSame = apiDateTimeFormatString.equals(generalDateTimeFormatString);

    apiDateTimeFormatter = DateTimeFormatter.ofPattern(apiDateTimeFormatString);
    generalDateTimeFormatter = DateTimeFormatter.ofPattern(generalDateTimeFormatString);
  }

  public String getGeneralDateTimeFormatString() {
    return generalDateTimeFormatString;
  }

  public DateTimeFormatter getGeneralDateTimeFormatter() {
    return generalDateTimeFormatter;
  }

  public String formatGeneralDateTime(final OffsetDateTime dateTime) {
    if (dateTime != null) {
      return dateTime.format(generalDateTimeFormatter);
    }
    return null;
  }

  public OffsetDateTime parseGeneralDateTime(final String dateTimeAsString) {
    if (StringUtils.isNotEmpty(dateTimeAsString)) {
      return OffsetDateTime.parse(dateTimeAsString, generalDateTimeFormatter);
    }
    return null;
  }

  public String getApiDateTimeFormatString() {
    return apiDateTimeFormatString;
  }

  public DateTimeFormatter getApiDateTimeFormatter() {
    return apiDateTimeFormatter;
  }

  public String formatApiDateTime(final OffsetDateTime dateTime) {
    if (dateTime != null) {
      return dateTime.format(apiDateTimeFormatter);
    }
    return null;
  }

  public OffsetDateTime parseApiDateTime(final String dateTimeAsString) {
    if (StringUtils.isNotEmpty(dateTimeAsString)) {
      return OffsetDateTime.parse(dateTimeAsString, apiDateTimeFormatter);
    }
    return null;
  }

  public String convertGeneralToApiDateTime(final String dateTimeAsString) {
    if (!storageAndApiFormatsAreSame && StringUtils.isNotEmpty(dateTimeAsString)) {
      final OffsetDateTime dateTime = parseGeneralDateTime(dateTimeAsString);
      return formatApiDateTime(dateTime);
    } else {
      return dateTimeAsString;
    }
  }
}
