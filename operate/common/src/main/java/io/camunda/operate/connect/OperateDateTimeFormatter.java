/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.connect;

import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.property.OperateProperties;
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
