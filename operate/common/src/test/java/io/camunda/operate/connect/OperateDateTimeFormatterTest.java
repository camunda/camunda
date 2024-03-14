/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.property.OperateElasticsearchProperties;
import io.camunda.operate.property.OperateOpensearchProperties;
import io.camunda.operate.property.OperateProperties;
import java.time.format.DateTimeParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class OperateDateTimeFormatterTest {

  private static final String RFC1339_DATETIMESTRING = "2024-02-15T22:40:10.834+00:00";
  private static final String DEFAULT_DATETIMESTRING = "2024-02-15T22:40:10.834+0000";

  @Mock private OperateProperties mockOperateProperties;

  @Mock private OperateElasticsearchProperties mockElasticsearchProperties;

  @Mock private OperateOpensearchProperties mockOpensearchProperties;

  @Mock private DatabaseInfo mockDatabaseInfo;

  private OperateDateTimeFormatter underTest;

  @Test
  public void testElasticsearchConfig() {
    when(mockDatabaseInfo.isOpensearchDb()).thenReturn(false);
    when(mockOperateProperties.isRfc3339ApiDateFormat()).thenReturn(false);
    when(mockOperateProperties.getElasticsearch()).thenReturn(mockElasticsearchProperties);
    when(mockElasticsearchProperties.getDateFormat())
        .thenReturn(OperateDateTimeFormatter.DATE_FORMAT_DEFAULT);

    underTest = new OperateDateTimeFormatter(mockOperateProperties, mockDatabaseInfo);

    verify(mockOperateProperties, times(1)).getElasticsearch();
    verify(mockElasticsearchProperties, times(1)).getDateFormat();
    verifyNoInteractions(mockOpensearchProperties);
  }

  @Test
  public void testOpensearchConfig() {
    when(mockDatabaseInfo.isOpensearchDb()).thenReturn(true);
    when(mockOperateProperties.isRfc3339ApiDateFormat()).thenReturn(false);
    when(mockOperateProperties.getOpensearch()).thenReturn(mockOpensearchProperties);
    when(mockOpensearchProperties.getDateFormat())
        .thenReturn(OperateDateTimeFormatter.DATE_FORMAT_DEFAULT);

    underTest = new OperateDateTimeFormatter(mockOperateProperties, mockDatabaseInfo);

    verify(mockOperateProperties, times(1)).getOpensearch();
    verify(mockOpensearchProperties, times(1)).getDateFormat();
    verifyNoInteractions(mockElasticsearchProperties);
  }

  @Test
  public void testRfc3339Config() {
    when(mockDatabaseInfo.isOpensearchDb()).thenReturn(false);
    when(mockOperateProperties.isRfc3339ApiDateFormat()).thenReturn(true);
    when(mockOperateProperties.getElasticsearch()).thenReturn(mockElasticsearchProperties);
    when(mockElasticsearchProperties.getDateFormat())
        .thenReturn(OperateDateTimeFormatter.DATE_FORMAT_DEFAULT);

    underTest = new OperateDateTimeFormatter(mockOperateProperties, mockDatabaseInfo);

    // Validate the datetime strings were set correctly
    assertThat(underTest.getGeneralDateTimeFormatString())
        .isEqualTo(OperateDateTimeFormatter.DATE_FORMAT_DEFAULT);
    assertThat(underTest.getApiDateTimeFormatString())
        .isEqualTo(OperateDateTimeFormatter.RFC3339_DATE_FORMAT);

    // Validate the datetime formatters were created correctly
    assertThat(underTest.getGeneralDateTimeFormatter().parse(DEFAULT_DATETIMESTRING)).isNotNull();
    assertThrows(
        DateTimeParseException.class,
        () -> underTest.getGeneralDateTimeFormatter().parse(RFC1339_DATETIMESTRING));
    assertThat(underTest.getApiDateTimeFormatter().parse(RFC1339_DATETIMESTRING)).isNotNull();
    assertThrows(
        DateTimeParseException.class,
        () -> underTest.getApiDateTimeFormatter().parse(DEFAULT_DATETIMESTRING));

    // Validate the parse functions
    assertThat(underTest.parseGeneralDateTime(DEFAULT_DATETIMESTRING)).isNotNull();
    assertThrows(
        DateTimeParseException.class, () -> underTest.parseGeneralDateTime(RFC1339_DATETIMESTRING));
    assertThat(underTest.parseApiDateTime(RFC1339_DATETIMESTRING)).isNotNull();
    assertThrows(
        DateTimeParseException.class, () -> underTest.parseApiDateTime(DEFAULT_DATETIMESTRING));

    // Validate the convert function
    assertThat(underTest.convertGeneralToApiDateTime(DEFAULT_DATETIMESTRING))
        .isEqualTo(RFC1339_DATETIMESTRING);
    assertThrows(
        DateTimeParseException.class,
        () -> underTest.convertGeneralToApiDateTime(RFC1339_DATETIMESTRING));
  }

  @Test
  public void testDefaultConfig() {
    when(mockDatabaseInfo.isOpensearchDb()).thenReturn(false);
    when(mockOperateProperties.isRfc3339ApiDateFormat()).thenReturn(false);
    when(mockOperateProperties.getElasticsearch()).thenReturn(mockElasticsearchProperties);
    when(mockElasticsearchProperties.getDateFormat())
        .thenReturn(OperateDateTimeFormatter.DATE_FORMAT_DEFAULT);

    underTest = new OperateDateTimeFormatter(mockOperateProperties, mockDatabaseInfo);

    assertThat(underTest.getApiDateTimeFormatString())
        .isEqualTo(OperateDateTimeFormatter.DATE_FORMAT_DEFAULT);

    // Validate the datetime strings were set correctly
    assertThat(underTest.getGeneralDateTimeFormatString())
        .isEqualTo(OperateDateTimeFormatter.DATE_FORMAT_DEFAULT);
    assertThat(underTest.getApiDateTimeFormatString())
        .isEqualTo(OperateDateTimeFormatter.DATE_FORMAT_DEFAULT);

    // Validate the datetime formatters were created correctly
    assertThat(underTest.getGeneralDateTimeFormatter().parse(DEFAULT_DATETIMESTRING)).isNotNull();
    assertThrows(
        DateTimeParseException.class,
        () -> underTest.getGeneralDateTimeFormatter().parse(RFC1339_DATETIMESTRING));
    assertThat(underTest.getApiDateTimeFormatter().parse(DEFAULT_DATETIMESTRING)).isNotNull();
    assertThrows(
        DateTimeParseException.class,
        () -> underTest.getApiDateTimeFormatter().parse(RFC1339_DATETIMESTRING));

    // Validate the parse functions
    assertThat(underTest.parseGeneralDateTime(DEFAULT_DATETIMESTRING)).isNotNull();
    assertThrows(
        DateTimeParseException.class, () -> underTest.parseGeneralDateTime(RFC1339_DATETIMESTRING));
    assertThat(underTest.parseApiDateTime(DEFAULT_DATETIMESTRING)).isNotNull();
    assertThrows(
        DateTimeParseException.class, () -> underTest.parseApiDateTime(RFC1339_DATETIMESTRING));

    // Validate the convert function
    assertThat(underTest.convertGeneralToApiDateTime(DEFAULT_DATETIMESTRING))
        .isEqualTo(DEFAULT_DATETIMESTRING);
  }
}
