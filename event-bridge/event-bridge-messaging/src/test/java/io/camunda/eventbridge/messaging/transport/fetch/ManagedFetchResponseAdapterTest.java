/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.messaging.transport.fetch;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.util.IndexEntry;
import io.camunda.zeebe.util.IndexScanResult;
import io.camunda.zeebe.util.ResourceLease;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCounted;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * The fetch reply hands its segment lease off to the transfer: Netty releases each {@link
 * SharedFileRegion} exactly once (after writing it, or when a failing channel discards it), and the
 * LAST region's release must close the lease — exactly once. Before this contract existed the
 * release signal was swallowed, every served segment stayed leased forever, and compacted segments
 * piled up on disk as {@code *-deleted} files the deferred deletion could never unlink.
 */
final class ManagedFetchResponseAdapterTest {

  private final AtomicInteger leaseReleases = new AtomicInteger();
  private final ResourceLease lease = leaseReleases::incrementAndGet;

  @Test
  void shouldReleaseTheLeaseOnlyAfterEveryRegionIsReleased() {
    // given a reply streaming three index entries as file regions
    final ManagedFetchResponseAdapter adapter = new ManagedFetchResponseAdapter(response(3));
    final List<Object> out = new ArrayList<>();
    adapter.encode(Unpooled.buffer(), out);
    final List<SharedFileRegion> regions =
        out.stream()
            .filter(SharedFileRegion.class::isInstance)
            .map(SharedFileRegion.class::cast)
            .toList();
    assertThat(regions).hasSize(3);

    // when Netty releases all but the last region, the lease stays held
    regions.get(0).release();
    regions.get(1).release();
    assertThat(leaseReleases).hasValue(0);

    // then the final region's release closes the lease, exactly once
    regions.get(2).release();
    assertThat(leaseReleases).hasValue(1);
  }

  @Test
  void shouldReleaseTheLeaseExactlyOnceAcrossDuplicateSignals() {
    // given an encoded reply whose regions have all been released
    final ManagedFetchResponseAdapter adapter = new ManagedFetchResponseAdapter(response(2));
    final List<Object> out = new ArrayList<>();
    adapter.encode(Unpooled.buffer(), out);
    out.stream()
        .filter(ReferenceCounted.class::isInstance)
        .map(ReferenceCounted.class::cast)
        .forEach(ReferenceCounted::release);
    assertThat(leaseReleases).hasValue(1);

    // when the transport also invokes the payload-level release (e.g. a failure path)
    adapter.release();

    // then the lease is not double-released — close() is a refcount decrement downstream
    assertThat(leaseReleases).hasValue(1);
  }

  @Test
  void shouldReleaseTheLeaseImmediatelyForAnEmptyReply() {
    // given a reply that streams no data (nothing for Netty to release later)
    final FetchResponse empty = new FetchResponse(0, -1, 42L, success(0), 0);
    final ManagedFetchResponseAdapter adapter = new ManagedFetchResponseAdapter(empty);

    // when it is encoded
    adapter.encode(Unpooled.buffer(), new ArrayList<>());

    // then the lease is closed right away
    assertThat(leaseReleases).hasValue(1);
  }

  private FetchResponse response(final int entries) {
    return new FetchResponse(1, entries, 42L, success(entries), entries * 10);
  }

  private IndexScanResult.Success success(final int entries) {
    final List<IndexEntry> index = new ArrayList<>();
    for (int i = 0; i < entries; i++) {
      index.add(new IndexEntry(i, i, i, i * 10, 10));
    }
    return new IndexScanResult.Success(null, index, lease);
  }
}
