/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.fs.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.zeebe.logstreams.impl.log.fs.FsLogSegment;
import io.zeebe.logstreams.impl.log.fs.FsLogSegments;
import java.util.List;
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
    final FsLogSegments fsLogSegments =
        FsLogSegments.fromFsLogSegmentsArray(List.of(firstSegment, secondSegment));

    final FsLogSegment segment = fsLogSegments.getFirst();

    assertThat(segment).isNotNull().isEqualTo(firstSegment);
  }

  @Test
  public void shouldGetFirstSegmentWithInitialSegmentId() {
    final FsLogSegments fsLogSegments =
        FsLogSegments.fromFsLogSegmentsArray(List.of(firstSegment, secondSegment));

    final FsLogSegment segment = fsLogSegments.getFirst();

    assertThat(segment).isNotNull().isEqualTo(firstSegment);
  }

  @Test
  public void shouldNotInitWithEmptyList() {

    assertThatThrownBy(() -> FsLogSegments.fromFsLogSegmentsArray(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldThrowOnGetFirstWhenAlreadyClosed() {
    // given
    final FsLogSegments segments = FsLogSegments.fromFsLogSegmentsArray(List.of(firstSegment));
    segments.closeAll();

    // expect - when
    assertThatThrownBy(segments::getFirst).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldThrowOnGetFirstSegmentIdWhenAlreadyClosed() {
    // given
    final FsLogSegments segments = FsLogSegments.fromFsLogSegmentsArray(List.of(firstSegment));
    segments.closeAll();

    // expect - when
    assertThatThrownBy(segments::getFirstSegmentId).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldThrowOnGetWhenAlreadyClosed() {
    // given
    final FsLogSegments segments = FsLogSegments.fromFsLogSegmentsArray(List.of(firstSegment));
    segments.closeAll();

    // expect - when
    assertThatThrownBy(() -> segments.getSegment(0)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldThrowOnGetLastSegmentIdWhenAlreadyClosed() {
    // given
    final FsLogSegments segments = FsLogSegments.fromFsLogSegmentsArray(List.of(firstSegment));
    segments.closeAll();

    // expect - when
    assertThatThrownBy(segments::getLastSegmentId).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldGetSegment() {
    final FsLogSegments fsLogSegments =
        FsLogSegments.fromFsLogSegmentsArray(List.of(firstSegment, secondSegment));

    assertThat(fsLogSegments.getSegment(0)).isEqualTo(firstSegment);
    assertThat(fsLogSegments.getSegment(1)).isEqualTo(secondSegment);
  }

  @Test
  public void shouldGetSegmentWithInitialSegmentId() {
    final FsLogSegments fsLogSegments =
        FsLogSegments.fromFsLogSegmentsArray(List.of(firstSegment, secondSegment));
    when(firstSegment.getSegmentId()).thenReturn(1);

    assertThat(fsLogSegments.getSegment(1)).isEqualTo(firstSegment);
    assertThat(fsLogSegments.getSegment(2)).isEqualTo(secondSegment);
  }

  @Test
  public void shouldNotGetSegmentIfNotExists() {
    final FsLogSegments fsLogSegments =
        FsLogSegments.fromFsLogSegmentsArray(List.of(firstSegment, secondSegment));
    when(firstSegment.getSegmentId()).thenReturn(1);

    assertThat(fsLogSegments.getSegment(0)).isNull();
    assertThat(fsLogSegments.getSegment(3)).isNull();
  }

  @Test
  public void shouldAddSegment() {
    final FsLogSegments fsLogSegments = FsLogSegments.fromFsLogSegmentsArray(List.of(firstSegment));

    fsLogSegments.addSegment(secondSegment);

    assertThat(fsLogSegments.getSegment(1)).isNotNull().isEqualTo(secondSegment);
  }

  @Test
  public void shouldCloseAllSegments() {
    final FsLogSegments fsLogSegments =
        FsLogSegments.fromFsLogSegmentsArray(List.of(firstSegment, secondSegment));

    fsLogSegments.closeAll();

    verify(firstSegment).closeSegment();
    verify(secondSegment).closeSegment();

    assertThatThrownBy(fsLogSegments::getFirst).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldDeleteUntilSegmentId() {
    // given
    final FsLogSegment thirdSegment = mock(FsLogSegment.class);
    final FsLogSegment fourthSegment = mock(FsLogSegment.class);
    final FsLogSegment fifthSegment = mock(FsLogSegment.class);

    when(firstSegment.getSegmentId()).thenReturn(1);
    when(secondSegment.getSegmentId()).thenReturn(2);
    when(thirdSegment.getSegmentId()).thenReturn(3);
    when(fourthSegment.getSegmentId()).thenReturn(4);
    when(fifthSegment.getSegmentId()).thenReturn(5);

    final FsLogSegments fsLogSegments =
        FsLogSegments.fromFsLogSegmentsArray(
            List.of(firstSegment, secondSegment, thirdSegment, fourthSegment, fifthSegment));

    // when
    fsLogSegments.deleteSegmentsUntil(3);

    // then
    assertThat(fsLogSegments.getFirstSegmentId()).isEqualTo(3);
    assertThat(fsLogSegments.getFirst()).isEqualTo(thirdSegment);
  }

  @Test
  public void shouldNotDeleteUntilWithNegativeSegmentId() {
    // given
    when(firstSegment.getSegmentId()).thenReturn(1);
    when(secondSegment.getSegmentId()).thenReturn(2);

    final FsLogSegments fsLogSegments =
        FsLogSegments.fromFsLogSegmentsArray(List.of(firstSegment, secondSegment));

    // when
    fsLogSegments.deleteSegmentsUntil(-1);

    // then
    assertThat(fsLogSegments.getFirstSegmentId()).isEqualTo(1);
    assertThat(fsLogSegments.getFirst()).isEqualTo(firstSegment);
    assertThat(fsLogSegments.getLastSegmentId()).isEqualTo(2);
  }
}
