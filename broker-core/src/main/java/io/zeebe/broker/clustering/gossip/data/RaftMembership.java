package io.zeebe.broker.clustering.gossip.data;

import static io.zeebe.clustering.gossip.PeerDescriptorDecoder.RaftMembershipsDecoder.partitionIdNullValue;
import static io.zeebe.clustering.gossip.PeerDescriptorDecoder.RaftMembershipsDecoder.termNullValue;
import static io.zeebe.logstreams.log.LogStream.MAX_TOPIC_NAME_LENGTH;
import static io.zeebe.util.StringUtil.fromBytes;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.broker.clustering.raft.Raft;
import io.zeebe.clustering.gossip.RaftMembershipState;


public class RaftMembership
{

    // either a reference to an actual raft, i.e. for the local peer
    private Raft reference;

    // or properties extracted from gossip messages
    private int partitionId = partitionIdNullValue();
    private int term = termNullValue();
    private RaftMembershipState state = RaftMembershipState.NULL_VAL;
    private int topicNameLength = 0;
    private final MutableDirectBuffer topicNameBuffer = new UnsafeBuffer(new byte[MAX_TOPIC_NAME_LENGTH]);

    public RaftMembership reference(final Raft reference)
    {
        this.reference = reference;

        return this;
    }

    public int partitionId()
    {
        if (reference != null)
        {
            return reference.stream().getPartitionId();
        }
        else
        {
            return this.partitionId;
        }
    }

    public RaftMembership partitionId(final int partitionId)
    {
        if (reference != null)
        {
            throw new IllegalArgumentException("Cannot set partition id on reference");
        }

        this.partitionId = partitionId;

        return this;
    }

    public int term()
    {
        if (reference != null)
        {
            return reference.term();
        }
        else
        {
            return term;
        }
    }

    public RaftMembership term(final int term)
    {
        if (reference != null)
        {
            throw new IllegalArgumentException("Cannot set term on reference");
        }

        this.term = term;

        return this;
    }

    public RaftMembershipState state()
    {
        if (reference != null)
        {
            return reference.state();
        }
        else
        {
            return state;
        }
    }

    public RaftMembership state(final RaftMembershipState state)
    {
        if (reference != null)
        {
            throw new IllegalArgumentException("Cannot set state on reference");
        }

        this.state = state;

        return this;
    }

    public DirectBuffer topicNameBuffer()
    {
        if (reference != null)
        {
            return reference.stream().getTopicName();
        }
        else
        {
            return topicNameBuffer;
        }
    }

    public MutableDirectBuffer topicNameMutableBuffer()
    {
        if (reference != null)
        {
            throw new IllegalArgumentException("Cannot get mutable topic name buffer from reference");
        }
        else
        {
            return topicNameBuffer;
        }
    }

    public String topicName()
    {
        final int length = topicNameLength();
        final byte[] tmp = new byte[length];
        topicNameBuffer().getBytes(0, tmp, 0, length);

        return fromBytes(tmp);
    }

    public RaftMembership topicName(final DirectBuffer src, final int offset, final int length)
    {
        checkTopicNameLength(length);
        topicNameBuffer.putBytes(0, src, offset, length);
        topicNameBuffer.setMemory(length, topicNameBuffer.capacity() - length, (byte) 0);
        topicNameLength = length;
        return this;
    }

    public int topicNameLength()
    {
        if (reference != null)
        {
            return reference.stream().getTopicName().capacity();
        }
        else
        {
            return topicNameLength;
        }
    }

    public RaftMembership topicNameLength(final int topicNameLength)
    {
        if (reference != null)
        {
            throw new IllegalArgumentException("Cannot set topic name on reference");
        }

        checkTopicNameLength(topicNameLength);

        this.topicNameLength = topicNameLength;

        return this;
    }

    private void checkTopicNameLength(final int length)
    {
        if (length > topicNameBuffer.capacity())
        {
            throw new RuntimeException(String.format("Topic name length exceeds max length (%d > %d bytes)", length, topicNameBuffer.capacity()));
        }
    }

    public void wrap(final RaftMembership other)
    {
        reset();

        if (other.reference != null)
        {
            reference = other.reference;
        }
        else
        {
            topicNameBuffer.getBytes(0, other.topicNameBuffer, 0, other.topicNameLength);
            topicNameLength = other.topicNameLength;
            partitionId = other.partitionId;
            term = other.term;
            state = other.state;

        }
    }

    public RaftMembership reset()
    {
        reference = null;

        partitionId = partitionIdNullValue();
        term = termNullValue();
        state = RaftMembershipState.NULL_VAL;

        topicNameLength = 0;
        topicNameBuffer.setMemory(0, topicNameBuffer.capacity(), (byte) 0);

        return this;
    }

    @Override
    public boolean equals(final Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        final RaftMembership that = (RaftMembership) o;

        if (partitionId() != that.partitionId())
        {
            return false;
        }
        if (term() != that.term())
        {
            return false;
        }
        if (state() != that.state())
        {
            return false;
        }
        return topicNameBuffer() != null ? topicNameBuffer().equals(that.topicNameBuffer()) : that.topicNameBuffer() == null;
    }

    @Override
    public int hashCode()
    {
        int result = partitionId();
        result = 31 * result + term();
        result = 31 * result + (state() != null ? state().hashCode() : 0);
        result = 31 * result + (topicNameBuffer() != null ? topicNameBuffer().hashCode() : 0);
        return result;
    }

    @Override
    public String toString()
    {
        return "RaftMembership{" +
            "partitionId=" + partitionId() +
            ", term=" + term() +
            ", state=" + state() +
            ", topicName=" + topicName() +
            ", reference=" + reference +
            '}';
    }
}
