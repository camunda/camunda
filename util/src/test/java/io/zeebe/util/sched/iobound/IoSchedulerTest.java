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
package io.zeebe.util.sched.iobound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import io.zeebe.util.sched.ActorTask;
import io.zeebe.util.sched.IoScheduler;
import java.util.function.IntFunction;
import org.junit.Test;
import org.mockito.InOrder;

@SuppressWarnings("unchecked")
public class IoSchedulerTest {
  @Test
  public void shouldLimitMaxConcurrency() {
    // given

    final ActorTask task = mock(ActorTask.class);
    final IntFunction<ActorTask> getTaskFn = mock(IntFunction.class);

    when(getTaskFn.apply(anyInt())).thenReturn(task);

    final IoScheduler ioScheduler = new IoScheduler(getTaskFn, new int[] {1, 2});

    // when + then

    assertThat(ioScheduler.getNextTask(null)).isNotNull();
    assertThat(ioScheduler.getNextTask(null)).isNotNull();
    assertThat(ioScheduler.getNextTask(null)).isNotNull();
    assertThat(ioScheduler.getNextTask(null)).isNull();

    final InOrder inOrder = inOrder(getTaskFn);
    inOrder.verify(getTaskFn, times(1)).apply(0);
    inOrder.verify(getTaskFn, times(2)).apply(1);
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void shouldReleaseTasks() {
    // given

    final ActorTask task = mock(ActorTask.class);
    final IntFunction<ActorTask> getTaskFn = mock(IntFunction.class);

    when(getTaskFn.apply(anyInt())).thenReturn(task);

    final IoScheduler ioScheduler = new IoScheduler(getTaskFn, new int[] {1, 2});

    assertThat(ioScheduler.getNextTask(null)).isNotNull();
    assertThat(ioScheduler.getNextTask(null)).isNotNull();
    assertThat(ioScheduler.getNextTask(null)).isNotNull();

    // when

    when(task.getDeviceId()).thenReturn(0);
    ioScheduler.onTaskReleased(task);

    // then

    assertThat(ioScheduler.getNextTask(null)).isNotNull();
    assertThat(ioScheduler.getNextTask(null)).isNull();
  }
}
