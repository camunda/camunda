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
package io.zeebe.db.impl.rocksdb;

import org.rocksdb.ReadOptions;
import org.rocksdb.ReadTier;
import org.rocksdb.Slice;
import org.rocksdb.Snapshot;

class RocksDbReadOptions extends ReadOptions {

  public long getNativeHandle() {
    return nativeHandle_;
  }

  RocksDbReadOptions() {
    super();
  }

  @Override
  public RocksDbReadOptions setVerifyChecksums(boolean verifyChecksums) {
    super.setVerifyChecksums(verifyChecksums);
    return this;
  }

  @Override
  public RocksDbReadOptions setFillCache(boolean fillCache) {
    super.setFillCache(fillCache);
    return this;
  }

  @Override
  public RocksDbReadOptions setSnapshot(Snapshot snapshot) {
    super.setSnapshot(snapshot);
    return this;
  }

  @Override
  public RocksDbReadOptions setReadTier(ReadTier readTier) {
    super.setReadTier(readTier);
    return this;
  }

  @Override
  public RocksDbReadOptions setTailing(boolean tailing) {
    super.setTailing(tailing);
    return this;
  }

  @Override
  public RocksDbReadOptions setManaged(boolean managed) {
    super.setManaged(managed);
    return this;
  }

  @Override
  public RocksDbReadOptions setTotalOrderSeek(boolean totalOrderSeek) {
    super.setTotalOrderSeek(totalOrderSeek);
    return this;
  }

  @Override
  public RocksDbReadOptions setPrefixSameAsStart(boolean prefixSameAsStart) {
    super.setPrefixSameAsStart(prefixSameAsStart);
    return this;
  }

  @Override
  public RocksDbReadOptions setPinData(boolean pinData) {
    super.setPinData(pinData);
    return this;
  }

  @Override
  public RocksDbReadOptions setBackgroundPurgeOnIteratorCleanup(
      boolean backgroundPurgeOnIteratorCleanup) {
    super.setBackgroundPurgeOnIteratorCleanup(backgroundPurgeOnIteratorCleanup);
    return this;
  }

  @Override
  public RocksDbReadOptions setReadaheadSize(long readaheadSize) {
    super.setReadaheadSize(readaheadSize);
    return this;
  }

  @Override
  public RocksDbReadOptions setIgnoreRangeDeletions(boolean ignoreRangeDeletions) {
    super.setIgnoreRangeDeletions(ignoreRangeDeletions);
    return this;
  }

  @Override
  public RocksDbReadOptions setIterateUpperBound(Slice iterateUpperBound) {
    super.setIterateUpperBound(iterateUpperBound);
    return this;
  }
}
