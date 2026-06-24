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
package io.camunda.client.impl.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

public class ParseUtilTest {
  @Test
  void shouldReturnNullWhenDateTimeIsNull() {
    // when
    final OffsetDateTime result = ParseUtil.parseOffsetDateTimeOrNull(null);

    // then
    assertThat(result).isNull();
  }

  /** {@link java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME} format */
  @Test
  void shouldParseIsoOffsetDateTime() {
    // given
    final String dateTime = "2025-12-03T12:00:00+03:00";

    // when
    final OffsetDateTime result = ParseUtil.parseOffsetDateTimeOrNull(dateTime);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getYear()).isEqualTo(2025);
    assertThat(result.getMonthValue()).isEqualTo(12);
    assertThat(result.getDayOfMonth()).isEqualTo(3);
    assertThat(result.getHour()).isEqualTo(12);
    assertThat(result.getMinute()).isEqualTo(0);
    assertThat(result.getSecond()).isEqualTo(0);
    assertThat(result.getOffset()).isEqualTo(ZoneOffset.ofHours(3));
  }

  /** {@link java.time.format.DateTimeFormatter.ISO_ZONED_DATE_TIME} format */
  @Test
  void shouldParseIsoZonedDateTime() {
    // given
    final String dateTime = "2025-12-04T12:00:00+01:00[Europe/Paris]";

    // when
    final OffsetDateTime result = ParseUtil.parseOffsetDateTimeOrNull(dateTime);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getYear()).isEqualTo(2025);
    assertThat(result.getMonthValue()).isEqualTo(12);
    assertThat(result.getDayOfMonth()).isEqualTo(4);
    assertThat(result.getHour()).isEqualTo(12);
    assertThat(result.getMinute()).isEqualTo(0);
    assertThat(result.getSecond()).isEqualTo(0);
    assertThat(result.getOffset()).isEqualTo(ZoneOffset.ofHours(1));
  }

  @Test
  void shouldThrowExceptionForInvalidDateFormat() {
    // given
    final String invalidDateTime = "not-a-date";

    // when/then
    assertThatThrownBy(() -> ParseUtil.parseOffsetDateTimeOrNull(invalidDateTime))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Failed to parse date: " + invalidDateTime);
  }
}
