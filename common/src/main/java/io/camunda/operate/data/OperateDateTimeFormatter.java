/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.data;

import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.property.OperateProperties;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class OperateDateTimeFormatter {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private final DateTimeFormatter apiDateTimeFormatter;
  private final DateTimeFormatter generalDateTimeFormatter;

  private final String apiDateTimeFormatString;
  private final String generalDateTimeFormatString;

  public static final String RFC3339_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSxxx";
  public static final String DATE_FORMAT_DEFAULT = "yyyy-MM-dd'T'HH:mm:ss.SSSZZ";

  public OperateDateTimeFormatter(OperateProperties operateProperties, DatabaseInfo databaseInfo) {
    if (databaseInfo.isOpensearchDb()) {
      generalDateTimeFormatString = operateProperties.getOpensearch().getDateFormat();
    }
    else {
      generalDateTimeFormatString = operateProperties.getElasticsearch().getDateFormat();
    }

    if (operateProperties.isRfc3339ApiDateFormat()) {
      logger.info("rfc3339ApiDateFormat is set to true, operate API will format datetimes according to the RFC3339 spec");
      apiDateTimeFormatString = RFC3339_DATE_FORMAT;
    }
    else {
      logger.info("rfc3339ApiDateFormat is set to false, operate API will format datetimes in the existing format");
      apiDateTimeFormatString = generalDateTimeFormatString;
    }

    this.apiDateTimeFormatter = DateTimeFormatter.ofPattern(apiDateTimeFormatString);
    this.generalDateTimeFormatter = DateTimeFormatter.ofPattern(generalDateTimeFormatString);
  }

  public String getGeneralDateTimeFormatString() {
    return generalDateTimeFormatString;
  }
  public DateTimeFormatter getGeneralDateTimeFormatter() {
    return generalDateTimeFormatter;
  }

  public String formatGeneralDateTime(OffsetDateTime dateTime) {
    if (dateTime != null) {
      return dateTime.format(generalDateTimeFormatter);
    }
    return null;
  }

  public OffsetDateTime parseGeneralDateTime(String dateTimeAsString) {
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

  public String formatApiDateTime(OffsetDateTime dateTime) {
    if (dateTime != null) {
      return dateTime.format(apiDateTimeFormatter);
    }
    return null;
  }

  public OffsetDateTime parseApiDateTime(String dateTimeAsString) {
    if (StringUtils.isNotEmpty(dateTimeAsString)) {
      return OffsetDateTime.parse(dateTimeAsString, apiDateTimeFormatter);
    }
    return null;
  }

  public String convertGeneralToApiDateTime(String dateTimeAsString) {
    if (StringUtils.isNotEmpty(dateTimeAsString)) {
      OffsetDateTime dateTime = parseGeneralDateTime(dateTimeAsString);
      return formatApiDateTime(dateTime);
    }
    return null;
  }


}
