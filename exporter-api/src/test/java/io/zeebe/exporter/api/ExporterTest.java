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
package io.zeebe.exporter.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.exporter.api.context.Context;
import io.zeebe.protocol.record.Record;
import java.io.IOException;
import org.junit.Test;

public final class ExporterTest {

  @Test
  public void shouldAllowExporterToThrowCheckedExceptions() {
    // given
    final Exception expectedException = new IOException("catch me");

    final Exporter exporter =
        new Exporter() {
          @Override
          public void configure(final Context context) throws Exception {
            throw expectedException;
          }

          @Override
          public void export(final Record<?> record) {}
        };

    // then
    assertThatThrownBy(() -> exporter.configure(null)).isEqualTo(expectedException);
  }
}
