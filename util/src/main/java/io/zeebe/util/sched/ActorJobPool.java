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
package io.zeebe.util.sched;

/**
 * Pool used for recycling {@link ActorJob} objects.
 *<p>
 * This pool is not threadsafe and each {@link ActorThread} must have their own local pool.
 *<p>
 * Jobs created by a pool instance are not "owned" by it. Due to work stealing or other
 * scheduling conditions, a job may be created by one runner but be executed by another.
 * In that case it is supplied by the first runner's pool and reclaimed by the second runner's
 * pool.
 */
public class ActorJobPool
{
    /*
     * This pool is implemented as a simple linked list of {@link ActorJob} objects using the 'next'
     * pointers in these objects.
     *
     * The variable 'first' holds a reference to the first object in the pool.
     * Upon invocation of the getNewTask() method, the pool checks if an object is pooled.
     * If true, it removes this object and sets the first pointer to the next object.
     * If false, a new object is created.
     *
     * Because the pool does not "own" the objects it creates, it must not reference them
     * after they have been supplied to a runner.
     *
     * Upon invocation of the reclaim() method, the first object is replaced by this object
     * and it's next
     * If the pool has reached it's capacity, the object is discarded and can be reclaimed by GC
     */

    /** Capacity of the pool. Controls how many objects can be pooled */
    private final int capacity;

    /** Current size of the pool; the number of tasks currently pooled */
    private int size;

    /** pointer to the first object in the pool */
    private ActorJob first;

    public ActorJobPool()
    {
        this(2048);
    }

    public ActorJobPool(int capacity)
    {
        this.capacity = capacity;

        // TODO: think about pre-filling the pool to some fraction of the capacity
    }

    /**
     * Returns a job (either recycled or new)
     */
    public ActorJob nextJob()
    {
        ActorJob j = first;

        if (j != null)
        {
            first = j.next;
            j.next = null;

            --size;
        }
        else
        {
            j = new ActorJob();
        }

        return j;
    }

    public void reclaim(ActorJob j)
    {
        if (size <= capacity)
        {
            j.reset();
            final ActorJob prev = first;
            j.next = prev;
            first = j;

            ++size;
        }
    }

    public int getCapacity()
    {
        return capacity;
    }

    public int getSize()
    {
        return size;
    }
}
