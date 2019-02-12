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

import static io.zeebe.model.bpmn.util.time.TimeDateTimer.parse;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.Test;

public class TimeDateTimerTest {
  @Test
  public void shouldParseTimeDateCorrectly() {
    // given
    final ZonedDateTime currentTime = ZonedDateTime.now();

    // when
    final Timer timeDate = TimeDateTimer.parse(currentTime.toString());

    // then
    assertThat(timeDate.getRepetitions()).isEqualTo(1);
    assertThat(timeDate.getDueDate(0)).isEqualTo(currentTime.toInstant().toEpochMilli());
  }

  @Test
  public void shouldConvertTimezoneCorrectly() {
    // given
    final ZonedDateTime localTime = ZonedDateTime.now();
    final ZonedDateTime utcTime = localTime.withZoneSameInstant(ZoneId.of("UTC"));

    // when
    final Timer localTimeDate = parse(localTime.toString());
    final Timer utcTimeDate = parse(utcTime.toString());

    // then
    assertThat(localTimeDate.getDueDate(0)).isEqualTo(utcTimeDate.getDueDate(0));
    assertThat(localTimeDate.getRepetitions()).isEqualTo(1);
    assertThat(utcTimeDate.getRepetitions()).isEqualTo(1);
  }
}
