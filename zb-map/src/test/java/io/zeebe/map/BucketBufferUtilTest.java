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
package io.zeebe.map;

import static java.lang.Integer.MAX_VALUE;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.map.types.LongKeyHandler;
import io.zeebe.map.types.LongValueHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** */
public class BucketBufferUtilTest {
  protected BucketBufferArray bucketBufferArray;

  private static final int MAX_KEY_LEN = SIZE_OF_LONG;
  private static final int MAX_VALUE_LEN = SIZE_OF_LONG;
  private static final int MIN_BLOCK_COUNT = 2;

  @Before
  public void init() {
    bucketBufferArray = new BucketBufferArray(MIN_BLOCK_COUNT, MAX_KEY_LEN, MAX_VALUE_LEN);
  }

  @After
  public void close() {
    bucketBufferArray.close();
  }

  @Test
  public void shouldGenerateTheBucketAddress() {
    // given
    final int bucketBufferId = 8189;
    final int bucketOffset = 4234323;

    // when
    final long bucketAddress = BucketBufferArray.getBucketAddress(bucketBufferId, bucketOffset);

    // then
    final int extractedBucketBufferId = (int) (bucketAddress >> 32);
    final int extractedBucketOffset = (int) bucketAddress;

    assertThat(extractedBucketBufferId).isEqualTo(bucketBufferId);
    assertThat(extractedBucketOffset).isEqualTo(bucketOffset);
  }

  @Test
  public void shouldGenerateTheBucketAddressTillIntMax() {
    // given
    final long bucketAddress = BucketBufferArray.getBucketAddress(MAX_VALUE, MAX_VALUE);

    final int extractedBucketBufferId = (int) (bucketAddress >> 32);
    final int extractedBucketOffset = (int) bucketAddress;

    assertThat(extractedBucketBufferId).isEqualTo(MAX_VALUE);
    assertThat(extractedBucketOffset).isEqualTo(MAX_VALUE);
  }

  @Test
  public void shouldContainBucketInToString() {
    // given bucketBufferArray
    bucketBufferArray.allocateNewBucket(1, 1);

    // when
    final String string = bucketBufferArray.toString();

    // then
    assertThat(string).isEqualTo("Bucket-1 contains 0.0 % of all blocks:[ Blocks:0 ,Overflow:0]\n");
  }

  @Test
  public void shouldContainOverflowInToString() {
    // given bucketBufferArray
    final long bucketAddress = bucketBufferArray.allocateNewBucket(1, 1);
    bucketBufferArray.overflow(bucketAddress);

    // when
    final String string = bucketBufferArray.toString();

    // then
    assertThat(string)
        .isEqualTo(
            "Bucket-1 contains 0.0 % of all blocks:[ Blocks:0 ,Overflow:1]\n"
                + "Overflow-Bucket-1 contains 0.0 % of all blocks:[ Blocks:0 ,Overflow:0]\n");
  }

  @Test
  public void shouldContainBlockInToString() {
    // given bucketBufferArray
    final LongKeyHandler keyHandler = new LongKeyHandler();
    final LongValueHandler valueHandler = new LongValueHandler();
    final long newBucketAddress = bucketBufferArray.allocateNewBucket(1, 1);

    keyHandler.theKey = 10;
    valueHandler.theValue = 0xFF;
    bucketBufferArray.addBlock(newBucketAddress, keyHandler, valueHandler);
    bucketBufferArray.getFirstBlockOffset();

    // when
    final String string = bucketBufferArray.toString();

    // then
    assertThat(string)
        .isEqualTo("Bucket-1 contains 100.0 % of all blocks:[ Blocks:1 ,Overflow:0]\n");
  }

  @Test
  public void shouldContainOverflowBlockInToString() {
    // given
    final long newBucketAddress = bucketBufferArray.allocateNewBucket(1, 1);
    final long overflowBucketAddress = bucketBufferArray.overflow(newBucketAddress);
    final LongKeyHandler keyHandler = new LongKeyHandler();
    final LongValueHandler valueHandler = new LongValueHandler();

    keyHandler.theKey = 10;
    valueHandler.theValue = 0xFF;
    bucketBufferArray.addBlock(overflowBucketAddress, keyHandler, valueHandler);
    bucketBufferArray.getFirstBlockOffset();

    // when
    final String string = bucketBufferArray.toString();

    // then
    assertThat(string)
        .isEqualTo(
            "Bucket-1 contains 0.0 % of all blocks:[ Blocks:0 ,Overflow:1]\n"
                + "Overflow-Bucket-1 contains 100.0 % of all blocks:[ Blocks:1 ,Overflow:0]\n");
  }
}
