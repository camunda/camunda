/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.util.actor;

import static io.zeebe.util.EnsureUtil.*;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import org.agrona.ErrorHandler;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;

public final class ActorSchedulerBuilder
{
    private int threadCount = 1;
    private int baseIterationsPerActor = 1;
    private IdleStrategy runnerIdleStrategy = new BackoffIdleStrategy(100, 10, TimeUnit.MICROSECONDS.toNanos(1), TimeUnit.MILLISECONDS.toNanos(1));
    private ErrorHandler runnerErrorHandler = Throwable::printStackTrace;

    private double imbalanceRunnerThreshold = 0.25;
    private Duration schedulerInitialBackoff = Duration.ofSeconds(1);
    private Duration schedulerMaxBackoff = Duration.ofSeconds(5);

    private Duration durationSamplePeriod = Duration.ofMillis(1);
    private int durationSampleCount = 128;

    public ActorSchedulerBuilder threadCount(int threadCount)
    {
        this.threadCount = threadCount;
        return this;
    }

    public ActorSchedulerBuilder baseIterationsPerActor(int baseIterationsPerActor)
    {
        this.baseIterationsPerActor = baseIterationsPerActor;
        return this;
    }

    public ActorSchedulerBuilder runnerIdleStrategy(IdleStrategy idleStrategy)
    {
        this.runnerIdleStrategy = idleStrategy;
        return this;
    }

    public ActorSchedulerBuilder runnerErrorHander(ErrorHandler errorHandler)
    {
        this.runnerErrorHandler = errorHandler;
        return this;
    }

    public ActorSchedulerBuilder imbalanceThreshold(double imbalanceThreshold)
    {
        this.imbalanceRunnerThreshold = imbalanceThreshold;
        return this;
    }

    public ActorSchedulerBuilder schedulerInitialBackoff(Duration initialBackoff)
    {
        this.schedulerInitialBackoff = initialBackoff;
        return this;
    }

    public ActorSchedulerBuilder schedulerMaxBackoff(Duration maxBackoff)
    {
        this.schedulerMaxBackoff = maxBackoff;
        return this;
    }

    public ActorSchedulerBuilder durationSamplePeriod(Duration samplePeriod)
    {
        this.durationSamplePeriod = samplePeriod;
        return this;
    }

    public ActorSchedulerBuilder durationSampleCount(int sampleCount)
    {
        this.durationSampleCount = sampleCount;
        return this;
    }

    public ActorScheduler build()
    {
        ensureGreaterThan("thread count", threadCount, 0);
        ensureGreaterThan("base iterations per actor", baseIterationsPerActor, 0);
        ensureNotNull("runner idle strategy", runnerIdleStrategy);
        ensureNotNull("runner error handler", runnerErrorHandler);
        ensureNotNullOrGreaterThan("duration sample period", durationSamplePeriod, Duration.ofNanos(0));
        ensureGreaterThan("duration sample count", durationSampleCount, 0);
        ensureLessThanOrEqual("imbalance threshold", imbalanceRunnerThreshold, 1.0);
        ensureGreaterThanOrEqual("imbalance threshold", imbalanceRunnerThreshold, 0.0);
        ensureNotNullOrGreaterThan("scheduler initial backoff", schedulerInitialBackoff, Duration.ofNanos(0));
        ensureNotNullOrGreaterThan("scheduler max backoff", schedulerMaxBackoff, schedulerInitialBackoff);

        final Supplier<ActorRunner> runnerFactory = () -> new ActorRunner(baseIterationsPerActor, runnerIdleStrategy, runnerErrorHandler, durationSamplePeriod);
        final Function<Actor, ActorReferenceImpl> actorRefFactory = task -> new ActorReferenceImpl(task, durationSampleCount);

        final ActorScheduler actorScheduler;
        if (threadCount > 1)
        {
            final Function<ActorRunner[], ActorSchedulerRunnable> schedulerFactory = runners -> new ActorSchedulerRunnable(runners, actorRefFactory, imbalanceRunnerThreshold, schedulerInitialBackoff, schedulerMaxBackoff);

            actorScheduler = new DynamicActorSchedulerImpl(threadCount, runnerFactory, schedulerFactory);
        }
        else
        {
            actorScheduler = new SingleThreadActorScheduler(runnerFactory, actorRefFactory);
        }

        return actorScheduler;
    }

    public static ActorScheduler createDefaultScheduler()
    {
        return new ActorSchedulerBuilder().build();
    }

    public static ActorScheduler createDefaultScheduler(int threadCount)
    {
        return new ActorSchedulerBuilder().threadCount(threadCount).build();
    }

}