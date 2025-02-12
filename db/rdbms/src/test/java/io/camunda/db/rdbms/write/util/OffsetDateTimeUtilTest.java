package io.camunda.db.rdbms.write.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

public class OffsetDateTimeUtilTest {

  @Test
  public void testAddDurationWithDays() {
    final OffsetDateTime dateTime = OffsetDateTime.of(2023, 10, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final OffsetDateTime result = OffsetDateTimeUtil.addDuration(dateTime, "10");
    assertThat(result).isEqualTo(OffsetDateTime.of(2023, 10, 11, 0, 0, 0, 0, ZoneOffset.UTC));
  }

  @Test
  public void testAddDurationWithIso8601() {
    final OffsetDateTime dateTime = OffsetDateTime.of(2023, 10, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final OffsetDateTime result = OffsetDateTimeUtil.addDuration(dateTime, "P10D");
    assertThat(result).isEqualTo(OffsetDateTime.of(2023, 10, 11, 0, 0, 0, 0, ZoneOffset.UTC));
  }

  @Test
  public void testAddDurationWithInvalidIso8601() {
    final OffsetDateTime dateTime = OffsetDateTime.of(2023, 10, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    assertThatThrownBy(() -> OffsetDateTimeUtil.addDuration(dateTime, "invalid"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid duration string");
  }

  @Test
  public void testAddDurationWithNegativeDays() {
    final OffsetDateTime dateTime = OffsetDateTime.of(2023, 10, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final OffsetDateTime result = OffsetDateTimeUtil.addDuration(dateTime, "-10");
    assertThat(result).isEqualTo(OffsetDateTime.of(2023, 9, 21, 0, 0, 0, 0, ZoneOffset.UTC));
  }
}
