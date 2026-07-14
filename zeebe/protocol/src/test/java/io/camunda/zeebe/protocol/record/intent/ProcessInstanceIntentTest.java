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
package io.camunda.zeebe.protocol.record.intent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class ProcessInstanceIntentTest {

  @Test
  void shouldClassifySuspendAndResumeAsProcessInstanceCommands() {
    // given / when / then
    assertThat(ProcessInstanceIntent.isProcessInstanceCommand(ProcessInstanceIntent.SUSPEND))
        .isTrue();
    assertThat(ProcessInstanceIntent.isProcessInstanceCommand(ProcessInstanceIntent.RESUME))
        .isTrue();
  }

  @Test
  void shouldNotClassifySuspendAndResumeAsBpmnElementCommands() {
    // given / when / then
    assertThat(ProcessInstanceIntent.isBpmnElementCommand(ProcessInstanceIntent.SUSPEND)).isFalse();
    assertThat(ProcessInstanceIntent.isBpmnElementCommand(ProcessInstanceIntent.RESUME)).isFalse();
  }

  @Test
  void shouldMarkSuspendedAndResumedAsEvents() {
    // given / when / then
    assertThat(ProcessInstanceIntent.SUSPENDED.isEvent()).isTrue();
    assertThat(ProcessInstanceIntent.RESUMED.isEvent()).isTrue();
  }

  @Test
  void shouldMarkSuspendAndResumeAsCommands() {
    // given / when / then
    assertThat(ProcessInstanceIntent.SUSPEND.isEvent()).isFalse();
    assertThat(ProcessInstanceIntent.RESUME.isEvent()).isFalse();
  }

  @Test
  void shouldDecodeSuspendAndResumeIntentsFromShort() {
    // given / when / then
    assertThat(ProcessInstanceIntent.from((short) 17)).isEqualTo(ProcessInstanceIntent.SUSPEND);
    assertThat(ProcessInstanceIntent.from((short) 18)).isEqualTo(ProcessInstanceIntent.SUSPENDED);
    assertThat(ProcessInstanceIntent.from((short) 19)).isEqualTo(ProcessInstanceIntent.RESUME);
    assertThat(ProcessInstanceIntent.from((short) 20)).isEqualTo(ProcessInstanceIntent.RESUMED);
  }
}
