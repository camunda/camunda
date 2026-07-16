/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import static io.camunda.eventbridge.broker.compaction.CompactionRecords.put;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Serialization determinism and loud failure on a missing or torn referenced segment. */
final class CompactionManifestTest {

  @TempDir Path dir;

  @Test
  void shouldRoundTripDeterministically() {
    // given a manifest with segments given out of order
    final var manifest =
        new CompactionManifest(
            CompactionManifest.VERSION_1,
            500,
            777,
            List.of(
                new CleanSegment("clean-b.log", 30, 64, 999),
                new CleanSegment("clean-a.log", 10, 48, 888)));

    // when
    final byte[] bytes = manifest.serialize();
    final CompactionManifest parsed = CompactionManifest.parse(bytes);

    // then — parse recovers the content, and re-serialization is byte-identical (deterministic
    // order)
    assertThat(parsed.cleanerPoint()).isEqualTo(500);
    assertThat(parsed.maxLogTimestamp()).isEqualTo(777);
    assertThat(parsed.segments()).extracting(CleanSegment::firstPosition).containsExactly(10L, 30L);
    assertThat(parsed.serialize()).isEqualTo(bytes);
  }

  @Test
  void shouldRejectUnknownVersion() {
    // given a manifest header with an unsupported version
    final byte[] bytes = "EBCOMPACT-MANIFEST\t99\nC\t0\nM\t0\n".getBytes(StandardCharsets.UTF_8);

    // when / then
    assertThatThrownBy(() -> CompactionManifest.parse(bytes))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unsupported manifest version");
  }

  @Test
  void shouldRejectManifestWithoutLogClock() {
    // given a manifest missing the mandatory maxLogTimestamp line
    final byte[] bytes = "EBCOMPACT-MANIFEST\t1\nC\t0\n".getBytes(StandardCharsets.UTF_8);

    // when / then
    assertThatThrownBy(() -> CompactionManifest.parse(bytes))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("maxLogTimestamp");
  }

  @Test
  void shouldFailToLoadWhenReferencedSegmentMissing() {
    // given a committed manifest whose segment file is then deleted
    final var store = new FileManifestStore(dir);
    final List<CleanSegment> segments = commitSingleSegment(store);
    deleteSegment(segments.get(0));

    // when / then
    assertThatThrownBy(store::latest)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("missing clean segment");
  }

  @Test
  void shouldFailToLoadWhenReferencedSegmentTorn() throws IOException {
    // given a committed manifest whose segment file is then truncated
    final var store = new FileManifestStore(dir);
    final List<CleanSegment> segments = commitSingleSegment(store);
    Files.write(dir.resolve(segments.get(0).fileName()), new byte[] {0x00});

    // when / then
    assertThatThrownBy(store::latest)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Torn clean segment");
  }

  private List<CleanSegment> commitSingleSegment(final FileManifestStore store) {
    final var writer = new CleanSegmentWriter(dir, 5, 1 << 20);
    writer.append(put(1, "a", "va"));
    final List<CleanSegment> segments = writer.finish();
    store.commit(
        new CompactionManifest(CompactionManifest.VERSION_1, 5, 1, segments),
        List.of(dir.resolve(segments.get(0).fileName())));
    return segments;
  }

  private void deleteSegment(final CleanSegment segment) {
    try {
      Files.delete(dir.resolve(segment.fileName()));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
