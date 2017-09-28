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

import static io.zeebe.map.BucketBufferArray.ABANDONED_BUCKET;

/**
 * Helper class to capsulate the bucket merging behavior.
 */
final class ZbMapBucketMergeHelper
{

    private final BucketBufferArray bucketBufferArrayRef;
    private final HashTable hashTableRef;
    private final int blockCountPerBucket;

    ZbMapBucketMergeHelper(BucketBufferArray bucketBufferArrayRef, HashTable hashTableRef, int blockCountPerBucket)
    {
        this.bucketBufferArrayRef = bucketBufferArrayRef;
        this.hashTableRef = hashTableRef;
        this.blockCountPerBucket = blockCountPerBucket;
    }

    /**
     * <p>Merging rules for normal buckets:</p>
     * <ul>
     *     <li>
     *         Bucket is last bucket. Only in that case a bucket can be removed, since the bucketAddress
     *         consists of the bucket buffer id and the bucket offset.
     *     </li>
     *     <li>
     *         Bucket depth is equal to the depth of the bucket which should be merged.
     *         If this is not the case then the parent bucket was split again.
     *         This other bucket have to be merged before with the parent.
     *         This is necessary to calculate the id and update the hash table correctly.
     *     </li>
     *     <li>
     *         Parent fill count plus the bucket fill count have to be less then the maximum bucket fill count.
     *         Would result the merging in a full parent bucket on the next add block a new split have to be done.
     *         This split will create the same bucket which was merged before, since this makes no sense
     *         we only merge if the relocation will not result in a filled bucket!
     *     </li>
     * </ul>
     *
     * <p>
     *     Are the rules above satisfied and the bucket is or has no overflow bucket, then the following is done.
     * </p>
     * <ul>
     *     <li>
     *         Remaining blocks of the bucket are relocated to the parent bucket.
     *     </li>
     *     <li>
     *         Bucket is removed from the BucketBufferArray.
     *     </li>
     *     <li>
     *         Hash table is updated with the parent bucket address.
     *     </li>
     *     <li>
     *         The new last bucket is tried to merged. This calls recursively this method.
     *     </li>
     * </ul>
     *
     * <p>
     * Since this method is recursively it is possible that one merge call can clean up a bunch of empty or mergable
     * buckets. It is also possible that merge is called on a bucket which has as ID {@link BucketBufferArray#ABANDONED_BUCKET}.
     * In that case the bucket will be removed if it is the last one. Like on the normal merge we will call
     * again {@link #tryMergingBuckets(long, int)} with the new last bucket address. If it is not the last one the method returns.
     * Also if the bucket id and the depth is zero, which means the first bucket was not splitted before the method simply returns.
     * </p>
     *
     * <p>
     *     As mentioned before overflow buckets and buckets which have a overflow bucket are handled differently.
     *     An overflow bucket can be identified on the bucket depth, which is equal to {@link BucketBufferArray#OVERFLOW_BUCKET}.
     *     If the overflow pointer is set on a bucket this means the bucket has an overflow bucket.
     * </p>
     *
     * <b>Has overflow bucket, then {@link #tryMergeBucketWhichHasOverflowBucket(long, long)} is called and the following procedure is done.</b>
     * <p>
     *     In that case we try to merge the overflow bucket in the current bucket.
     *     Here also exist different cases:
     *     <ul>
     *         <li>
     *             Overflow bucket is removable and blocks of overflow bucket can be relocated to the current bucket,
     *             then we relocate the blocks and remove the overflow bucket. Like on the normal merge we will call
     *             again {@link #tryMergingBuckets(long, int)} with the new last bucket address.
     *         </li>
     *         <li>
     *             Overflow bucket is removable but can't be merged, then nothing happens.
     *         </li>
     *         <li>
     *             Overflow bucket is not removable but it can be merged
     *             Existing blocks are relocated to the current bucket and the overflow pointer is removed.
     *             Overflow bucket is marked as removable, see {@link BucketBufferArray#ABANDONED_BUCKET}.
     *         </li>
     *         <li>
     *             Overflow bucket is not removable and can't be merged, then nothing happens.
     *         </li>
     *     </ul>
     * </p>
     *
     * <b>Is overflow bucket, then {@link #tryMergeOverflowBucket(long, int, int)} is called and the following procedure is done.</b>
     *
     * <ul>
     *     <li>
     *          Is overflow bucket removable and the block can be relocated to the parent/original bucket,
     *          then this will be done. Like on the normal merge we will call
     *          again {@link #tryMergingBuckets(long, int)} with the new last bucket address.
     *     </li>
     *     <li>
     *         If the overflow bucket is not removable the next steps are depending, whether the overflow bucket has
     *         a overflow bucket or not.
     *         <ul>
     *             <li>
     *                  <p>
     *                      Is the overflow bucket without next overflow buckets, then we try to merge it into the parent/original bucket.
     *                      Since per definition overflow buckets are only created if a bucket is full and if we remove a block
     *                      from a bucket with an overflow bucket it tries to merge it, see next bullet point.
     *                      So we can assume that all overflow buckets in between the parent bucket and the current overflow bucket
     *                      are not mergeable with the current bucket.
     *                      We only have to check if it is possible to merge the overflow bucket into the parent bucket.
     *                  </p>
     *                  If the blocks of overflow bucket are relocatable to the parent bucket, this will be done and the overflow bucket pointer is removed.
     *                  Overflow bucket is marked as removable, see {@link BucketBufferArray#ABANDONED_BUCKET}.
     *             </li>
     *             <li>
     *                  If the overflow bucket has also an overflow bucket pointer it tries to merge this overflow bucket into the current one.
     *                  <ul>
     *                      <li>
     *                          Are the blocks relocatable to the current overflow bucket and the overflow bucket is removable, then this is done and the overflow
     *                          bucket is removed. Like on the normal merge we will call again {@link #tryMergingBuckets(long, int)} with the new last bucket address.
     *                      </li>
     *                      <li>
     *                          Are the blocks relocatable to the current overflow bucket and the overflow bucket is NOT removable, then the blocks are relocated
     *                          and the overflow bucket is marked as removable, see {@link BucketBufferArray#ABANDONED_BUCKET}.
     *                      </li>
     *                      <li>
     *                          Otherwise nothing happens.
     *                      </li>
     *                  </ul>
     *             </li>
     *         </ul>
     *     </li>
     * </ul>
     *
     *
     *
     *<p><b>Example:</b></p>
     *<pre>
     * Bucket 0 [A, B]
     * Bucket 1 [C, D] -> O3
     * Bucket 2 [E, F] -> O1
     * Bucket 3 [G, -]
     * O1       [H, I] -> O2
     * O2       [J, -]
     * O2       [K, -]
     * </pre>
     *
     * <ul>
     *     <li>
     *          Scenario A) removing Block J
     *          Checks for O2:
     *              -> is not removable
     *              -> has no overflow
     *              -> not mergable with original bucket 2
     *              -> DOES NOTHING
     *     </li>
     *     <li>
     *         Scenario B)
     *         <pre>
     *         removing H
     *         Checks for O1
     *              -> is not removable
     *              -> has overflow
     *              -> is not mergable
     *              -> DOES NOTHING
     *         </pre>
     *         <pre>
     *         removing I
     *         Checks for O1
     *              -> is not removable
     *              -> has overflow
     *              -> is mergable
     *              -> relocates J from O2 to O1
     *              -> O2 is not removable
     *              -> markes O2 as removable
     *         </pre>
     *     </li>
     * </ul>
     *
     * @param bucketAddress address of the bucket which should be as first tried to be merged
     * @param newBucketFillCount the new fill count of the bucket
     */
    void tryMergingBuckets(long bucketAddress, int newBucketFillCount)
    {
        final int bucketId = bucketBufferArrayRef.getBucketId(bucketAddress);
        final int depth = bucketBufferArrayRef.getBucketDepth(bucketAddress);

        if (splitWasCalledAtLeastOnce(bucketId, depth))
        {
            long newLastBucketAddress = 0;
            if (bucketId == ABANDONED_BUCKET)
            {
                newLastBucketAddress = tryRemoveAbandonedBucketWithoutMerge(bucketAddress);
            }
            else if (depth == BucketBufferArray.OVERFLOW_BUCKET)
            {
                newLastBucketAddress = tryMergeOverflowBucket(bucketAddress, newBucketFillCount, bucketId);
            }
            else
            {
                final long bucketOverflowPointer = bucketBufferArrayRef.getBucketOverflowPointer(bucketAddress);
                if (bucketOverflowPointer != 0)
                {
                    newLastBucketAddress = tryMergeBucketWhichHasOverflowBucket(bucketAddress, bucketOverflowPointer);
                }
                else
                {
                    newLastBucketAddress = tryMergeSplitBucket(bucketAddress, newBucketFillCount, depth, bucketId);
                }
            }

            /* disable recursive merging till https://github.com/zeebe-io/zeebe/issues/466 is fixed
            // recursion to try merging more buckets
            if (newLastBucketAddress > 0)
            {
                final int bucketFillCount = bucketBufferArrayRef.getBucketFillCount(newLastBucketAddress);
                tryMergingBuckets(newLastBucketAddress, bucketFillCount);
            }
            */
        }
    }

    /**
     * Checks if split was called at least once.
     * If both bucket id and depth are zero no split was called.
     *
     * @param id the bucket id
     * @param depth the bucket depth
     * @return true if split was called at least once, false otherwise
     */
    private boolean splitWasCalledAtLeastOnce(int id, int depth)
    {
        return id != 0 || depth != 0;
    }

    /**
     * Removes the bucket with the given address if possible.
     *
     * @param bucketAddress the address of the bucket which should be removed
     * @return zero if no bucket was removed or no remaining bucket exist, otherwise the address of the new last bucket
     *          which can be removed
     */
    private long tryRemoveAbandonedBucketWithoutMerge(long bucketAddress)
    {
        long newLastBucketAddress = 0;
        if (bucketBufferArrayRef.isBucketRemoveable(bucketAddress))
        {
            newLastBucketAddress = bucketBufferArrayRef.removeBucket(bucketAddress);
        }
        return newLastBucketAddress;
    }

    /**
     * Tries to merges overflow bucket into the parent bucket. If the overflow bucket has again an overflow bucket
     * it tries to merge the overflow bucket into the current overflow bucket, see {@link #tryMergeBucketWhichHasOverflowBucket(long, long)}.
     *
     * @param bucketAddress
     * @param newBucketFillCount
     * @param bucketId
     * @return zero if no bucket was removed or no remaining bucket exist, otherwise the address of the new last bucket
     *          which can be removed
     */
    private long tryMergeOverflowBucket(long bucketAddress, int newBucketFillCount, int bucketId)
    {
        final long parentBucketAddress = hashTableRef.getBucketAddress(bucketId);
        long newLastBucketAddress = 0;

        if (bucketBufferArrayRef.isBucketRemoveable(bucketAddress))
        {
            if (isMergableWithParentBucket(parentBucketAddress, newBucketFillCount))
            {
                bucketBufferArrayRef.relocateBlocksFromBucket(bucketAddress, parentBucketAddress);
                bucketBufferArrayRef.removeOverflowBucket(parentBucketAddress, bucketAddress);
                newLastBucketAddress = bucketBufferArrayRef.removeBucket(bucketAddress);
            }
        }
        else
        {
            final long bucketOverflowPointer = bucketBufferArrayRef.getBucketOverflowPointer(bucketAddress);
            if (bucketOverflowPointer != 0)
            {
                newLastBucketAddress = tryMergeBucketWhichHasOverflowBucket(bucketAddress, bucketOverflowPointer);
            }
            else if (isMergableWithParentBucket(parentBucketAddress, newBucketFillCount))
            {
                bucketBufferArrayRef.relocateBlocksFromBucket(bucketAddress, parentBucketAddress);
                bucketBufferArrayRef.removeOverflowBucket(parentBucketAddress, bucketAddress);
                bucketBufferArrayRef.setBucketId(bucketAddress, ABANDONED_BUCKET);
            }
        }
        return newLastBucketAddress;
    }

    /**
     * Tries to merge the overflow bucket into the original bucket.
     *
     * @param bucketAddress
     * @param bucketOverflowPointer
     * @return zero if no bucket was removed or no remaining bucket exist, otherwise the address of the new last bucket
     *          which can be removed
     */
    private long tryMergeBucketWhichHasOverflowBucket(long bucketAddress, long bucketOverflowPointer)
    {
        long newLastBucketAddress = 0;
        final long parentBucketAddress = bucketAddress;
        bucketAddress = bucketOverflowPointer;
        final int newBucketFillCount = bucketBufferArrayRef.getBucketFillCount(bucketAddress);

        if (isMergableWithParentBucket(parentBucketAddress, newBucketFillCount))
        {
            bucketBufferArrayRef.relocateBlocksFromBucket(bucketAddress, parentBucketAddress);
            bucketBufferArrayRef.removeOverflowBucket(parentBucketAddress, bucketAddress);

            if (bucketBufferArrayRef.isBucketRemoveable(bucketAddress))
            {
                newLastBucketAddress = bucketBufferArrayRef.removeBucket(bucketAddress);
            }
            else
            {
                bucketBufferArrayRef.setBucketId(bucketAddress, ABANDONED_BUCKET);
            }
        }

        return newLastBucketAddress;
    }

    /**
     * Tries to merge normal bucket with the parent bucket.
     * Merges the bucket if the merge rules are satisfied, see {@link #tryMergingBuckets(long, int)}.
     *
     * @param bucketAddress
     * @param newBucketFillCount
     * @param depth
     * @param bucketId
     * @return zero if no bucket was removed or no remaining bucket exist, otherwise the address of the new last bucket
     *          which can be removed
     */
    private long tryMergeSplitBucket(long bucketAddress, int newBucketFillCount, int depth, int bucketId)
    {
        final int id = (1 << (depth - 1)) ^ bucketId;
        final long parentBucketAddress;
        final int parentBucketDepth;

        if (id < bucketId)
        {
            // parent
            parentBucketAddress = hashTableRef.getBucketAddress(id);
            parentBucketDepth = bucketBufferArrayRef.getBucketDepth(parentBucketAddress);
        }
        else
        {
            // id is larger then current bucket id -> id is the id of the last splitted child
            parentBucketAddress = bucketAddress;
            parentBucketDepth = depth;

            bucketId = id;
            bucketAddress = hashTableRef.getBucketAddress(id);
            depth = bucketBufferArrayRef.getBucketDepth(bucketAddress);
            newBucketFillCount = bucketBufferArrayRef.getBucketFillCount(bucketAddress);
        }

        return tryToMergeSplitBucketWithParent(parentBucketAddress, parentBucketDepth,
                                               bucketAddress, depth, bucketId, newBucketFillCount);
    }

    /**
     * Tries to merge normal bucket with the parent bucket.
     * Merges the bucket if the merge rules are satisfied, see {@link #tryMergingBuckets(long, int)}.
     *
     * @param parentBucketAddress
     * @param parentBucketDepth
     * @param bucketAddress
     * @param depth
     * @param bucketId
     * @param newBucketFillCount
     * @return zero if no bucket was removed or no remaining bucket exist, otherwise the address of the new last bucket
     *          which can be removed
     */
    private long tryToMergeSplitBucketWithParent(long parentBucketAddress,
                                                 int parentBucketDepth,
                                                 long bucketAddress,
                                                 int depth,
                                                 int bucketId,
                                                 int newBucketFillCount)
    {
        long newLastBucketAddress = 0;

        if (parentBucketDepth == depth &&
            bucketBufferArrayRef.isBucketRemoveable(bucketAddress) &&
            isMergableWithParentBucket(parentBucketAddress, newBucketFillCount))
        {
            // merging
            // 1. relocate existing blocks to parent
            bucketBufferArrayRef.relocateBlocksFromBucket(bucketAddress, parentBucketAddress);
            // 2. remove bucket
            newLastBucketAddress = bucketBufferArrayRef.removeBucket(bucketAddress);
            // 3. update hash table like on split
            hashTableRef.updateTable(depth, bucketId, parentBucketAddress);
            // 4. decrease parent bucket depth
            bucketBufferArrayRef.setBucketDepth(parentBucketAddress, depth - 1);
        }
        return newLastBucketAddress;
    }

    /**
     * Checks if parent bucket with the given address is mergable with the given fill count.
     *
     * @param parentBucketAddress
     * @param bucketFillCount
     * @return true if mergable, false otherwise
     */
    private boolean isMergableWithParentBucket(long parentBucketAddress, int bucketFillCount)
    {
        final int parentFillCount = bucketBufferArrayRef.getBucketFillCount(parentBucketAddress);
        return (parentFillCount + bucketFillCount < blockCountPerBucket);
    }
}
