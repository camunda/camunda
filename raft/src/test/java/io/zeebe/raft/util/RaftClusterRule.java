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
package io.zeebe.raft.util;

import static io.zeebe.protocol.clientapi.EventType.NOOP_EVENT;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.raft.state.RaftState;
import io.zeebe.test.util.TestUtil;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RaftClusterRule implements TestRule
{

    public static final int DEFAULT_RETRIES = 20;
    public static final int COMMITTED_RETRIES = 40;
    public static final int ALL_COMMITTED_RETRIES = 100;

    public static final Logger LOG = LoggerFactory.getLogger("io.zeebe.raft.test");

    protected final BrokerEventMetadata metadata = new BrokerEventMetadata();

    private final ActorSchedulerRule actorScheduler;
    private final List<RaftRule> rafts;

    public RaftClusterRule(final ActorSchedulerRule actorScheduler, final RaftRule... rafts)
    {
        this.actorScheduler = actorScheduler;
        this.rafts = rafts != null ? new ArrayList<>(Arrays.asList(rafts)) : Collections.emptyList();
    }

    @Override
    public Statement apply(Statement base, final Description description)
    {
        final List<TestRule> rules = new ArrayList<>();
        rules.add(actorScheduler);
        rules.addAll(rafts);
        Collections.reverse(rules);

        for (final TestRule rule : rules)
        {
            base = rule.apply(base, description);
        }

        return base;
    }

    public List<RaftRule> getRafts()
    {
        return rafts;
    }

    public RaftClusterRule registerRaft(final RaftRule raft)
    {
        raft.clearSubscription();

        raft.schedule();
        this.rafts.add(raft);

        return this;
    }

    public RaftClusterRule registerRafts(final RaftRule... rafts)
    {
        for (final RaftRule raft : rafts)
        {
            registerRaft(raft);
        }

        return this;
    }

    public RaftClusterRule removeRaft(final RaftRule raft)
    {
        raft.unschedule();
        this.rafts.remove(raft);

        return this;
    }

    public RaftClusterRule removeRafts(final RaftRule... rafts)
    {
        for (final RaftRule raft : rafts)
        {
            removeRaft(raft);
        }

        return this;
    }

    public void awaitRaftState(final RaftRule raft, final RaftState state)
    {
        awaitCondition(() -> raft.getState() == state, "Failed to wait for %s to become %s", raft, state);
    }

    public void awaitLogControllerOpen(final RaftRule raft)
    {
        awaitCondition(() -> raft.getLogStream().getLogStreamController() != null, "Failed to wait for %s to open log stream controller", raft);
    }

    public void awaitEventCommitted(final RaftRule raftToWait, final long position, final int term, final String message)
    {
        awaitCondition(() -> raftToWait.eventCommitted(position, term, message), COMMITTED_RETRIES,
            "Failed to wait for commit of event %d/%d with message on raft %s", position, term, message, raftToWait);
    }

    public void awaitEventCommittedOnAll(final long position, final int term, final String message)
    {
        awaitCondition(() -> rafts.stream().allMatch(raft -> raft.eventCommitted(position, term, message)), ALL_COMMITTED_RETRIES,
            "Failed to wait for commit of event %d/%d with message on all rafts", position, term, message);
    }

    public void awaitEventsCommittedOnAll(final String... messages)
    {
        awaitCondition(() -> rafts.stream().allMatch(raft -> raft.eventsCommitted(messages)), ALL_COMMITTED_RETRIES,
            "Failed to wait for events {} to be commit on all rafts", Arrays.asList(messages));
    }

    public void awaitEventAppended(final RaftRule raftToWait, final long position, final int term, final String message)
    {
        awaitCondition(() -> raftToWait.eventAppended(position, term, message), COMMITTED_RETRIES,
            "Failed to wait for appended of event %d/%d with message on raft %s", position, term, message, raftToWait);
    }

    public void awaitEventAppendedOnAll(final long position, final int term, final String message)
    {
        awaitCondition(() -> rafts.stream().allMatch(raft -> raft.eventAppended(position, term, message)), ALL_COMMITTED_RETRIES,
            "Failed to wait for commit of event %d/%d with message on all rafts", position, term, message);
    }

    public void awaitInitialEventCommitted(final RaftRule raftToWait, final int term)
    {
        awaitCondition(() -> raftToWait.eventCommitted(term, NOOP_EVENT), COMMITTED_RETRIES,
            "Failed to wait for initial event of term %d to be committed on %s log stream", term, raftToWait);

    }

    public void awaitInitialEventCommittedOnAll(final int term)
    {
        awaitCondition(() -> rafts.stream().allMatch(raft -> raft.eventCommitted(term, NOOP_EVENT)), ALL_COMMITTED_RETRIES,
            "Failed to wait for initial event of term %d to be committed on all log streams", term);
    }

    public void awaitRaftEventCommitted(final RaftRule raftToWait, final int term, final RaftRule... members)
    {
        awaitCondition(() -> raftToWait.raftEventCommitted(term, members), COMMITTED_RETRIES,
            "Failed to wait for raft event of term %d with members %s to be committed on %s log stream", term, Arrays.asList(members), raftToWait);
    }

    public void awaitRaftEventCommittedOnAll(final int term)
    {
        awaitRaftEventCommittedOnAll(term, rafts.toArray(new RaftRule[rafts.size()]));
    }

    public void awaitRaftEventCommittedOnAll(final int term, final RaftRule... members)
    {
        awaitCondition(() -> rafts.stream().allMatch(raft -> raft.raftEventCommitted(term, members)), ALL_COMMITTED_RETRIES,
            "Failed to wait for raft event of term %d with members %s to be committed on all log streams", term, Arrays.asList(members));
    }

    public RaftRule awaitLeader()
    {
        return awaitCondition(() -> rafts.stream().filter(RaftRule::isLeader).findAny(), ALL_COMMITTED_RETRIES,
            "Failed to wait for a node to become leader in the cluster");
    }

    public void printLogEntries(final boolean readUncommitted)
    {
        rafts.forEach(r -> printLogEntries(r, readUncommitted));
    }

    public void printLogEntries(final RaftRule raft, final boolean readUncommitted)
    {
        LOG.error("Log entries for raft {}", raft.getSocketAddress());

        final LogStream logStream = raft.getLogStream();
        final long commitPosition = logStream.getCommitPosition();
        final BufferedLogStreamReader reader = new BufferedLogStreamReader(logStream, readUncommitted);
        reader.seekToFirstEvent();

        while (reader.hasNext())
        {
            final LoggedEvent next = reader.next();
            next.readMetadata(metadata);

            String message = "";

            if (metadata.getEventType() == EventType.NULL_VAL)
            {
                try
                {
                    message = ", message: " + bufferAsString(next.getValueBuffer(), next.getValueOffset(), next.getValueLength());
                }
                catch (final Exception e)
                {
                    // ignore
                }
            }

            LOG.error("Event { position: {}, term: {}, type: {}, committed: {}{} }", next.getPosition(), metadata.getRaftTermId(), metadata.getEventType(), next.getPosition() <= commitPosition, message);
        }
    }

    protected void awaitCondition(final BooleanSupplier supplier, final String message, final Object... args)
    {
        awaitCondition(supplier, DEFAULT_RETRIES, message, args);
    }

    protected void awaitCondition(final BooleanSupplier supplier, final int retires, final String message, final Object... args)
    {
        try
        {
            TestUtil.waitUntil(supplier, retires, message, args);
        }
        catch (final Throwable e)
        {
            printLogEntries(true);
            throw e;
        }
    }

    protected <T> T awaitCondition(final Supplier<Optional<T>> supplier, final String message, final Object... args)
    {
        return awaitCondition(supplier, DEFAULT_RETRIES, message, args);
    }

    protected <T> T awaitCondition(final Supplier<Optional<T>> supplier, final int retires, final String message, final Object... args)
    {
        awaitCondition(() -> supplier.get().isPresent(), retires, message, args);

        return supplier.get().orElseThrow(() -> new AssertionError("Failed get retrieve result"));
    }

}
