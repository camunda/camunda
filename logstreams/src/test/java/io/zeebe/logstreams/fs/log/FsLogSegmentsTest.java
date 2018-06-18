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
package io.zeebe.logstreams.fs.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.zeebe.logstreams.impl.log.fs.FsLogSegment;
import io.zeebe.logstreams.impl.log.fs.FsLogSegments;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class FsLogSegmentsTest {
  @Mock protected FsLogSegment firstSegment;

  @Mock protected FsLogSegment secondSegment;

  @Before
  public void init() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void shouldGetFirstSegment() {
    final FsLogSegments fsLogSegments = new FsLogSegments();

    fsLogSegments.init(0, new FsLogSegment[] {firstSegment, secondSegment});

    final FsLogSegment segment = fsLogSegments.getFirst();

    assertThat(segment).isNotNull().isEqualTo(firstSegment);
  }

  @Test
  public void shouldGetFirstSegmentWithInitialSegmentId() {
    final FsLogSegments fsLogSegments = new FsLogSegments();

    fsLogSegments.init(1, new FsLogSegment[] {firstSegment, secondSegment});

    final FsLogSegment segment = fsLogSegments.getFirst();

    assertThat(segment).isNotNull().isEqualTo(firstSegment);
  }

  @Test
  public void shouldNotGetFirstSegmentIfEmpty() {
    final FsLogSegments fsLogSegments = new FsLogSegments();

    fsLogSegments.init(0, new FsLogSegment[0]);

    final FsLogSegment segment = fsLogSegments.getFirst();

    assertThat(segment).isNull();
  }

  @Test
  public void shouldGetSegment() {
    final FsLogSegments fsLogSegments = new FsLogSegments();

    fsLogSegments.init(0, new FsLogSegment[] {firstSegment, secondSegment});

    assertThat(fsLogSegments.getSegment(0)).isEqualTo(firstSegment);
    assertThat(fsLogSegments.getSegment(1)).isEqualTo(secondSegment);
  }

  @Test
  public void shouldGetSegmentWithInitialSegmentId() {
    final FsLogSegments fsLogSegments = new FsLogSegments();

    fsLogSegments.init(1, new FsLogSegment[] {firstSegment, secondSegment});

    assertThat(fsLogSegments.getSegment(1)).isEqualTo(firstSegment);
    assertThat(fsLogSegments.getSegment(2)).isEqualTo(secondSegment);
  }

  @Test
  public void shouldNotGetSegmentIfNotExists() {
    final FsLogSegments fsLogSegments = new FsLogSegments();

    fsLogSegments.init(1, new FsLogSegment[] {firstSegment, secondSegment});

    assertThat(fsLogSegments.getSegment(0)).isNull();
    assertThat(fsLogSegments.getSegment(3)).isNull();
  }

  @Test
  public void shouldAddSegment() {
    final FsLogSegments fsLogSegments = new FsLogSegments();

    fsLogSegments.init(0, new FsLogSegment[] {firstSegment});

    fsLogSegments.addSegment(secondSegment);

    assertThat(fsLogSegments.getSegment(1)).isNotNull().isEqualTo(secondSegment);
  }

  @Test
  public void shouldCloseAllSegments() {
    final FsLogSegments fsLogSegments = new FsLogSegments();

    fsLogSegments.init(0, new FsLogSegment[] {firstSegment, secondSegment});

    fsLogSegments.closeAll();

    verify(firstSegment).closeSegment();
    verify(secondSegment).closeSegment();

    assertThat(fsLogSegments.getFirst()).isNull();
  }
}
