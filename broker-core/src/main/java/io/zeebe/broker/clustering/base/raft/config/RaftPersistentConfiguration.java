/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.clustering.base.raft.config;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import io.zeebe.raft.RaftPersistentStorage;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.StreamUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Represents the configuration that Raft persists locally on the filesystem.
 *<p>
 * In addition to protocol state managed through {@link RaftPersistentStorage}, we keep
 * <ul>
 * <li>partition id</li>
 * <li>topic name</li>
 * <li>directory path of the local data directory of the logstream used</li>
 * </ul>
 */
public class RaftPersistentConfiguration implements RaftPersistentStorage
{
    private final RaftConfigurationMetadata configuration = new RaftConfigurationMetadata();

    private final File file;
    private final File tmpFile;
    private final Path path;
    private final Path tmpPath;

    private final MutableDirectBuffer buffer = new UnsafeBuffer(0, 0);

    private final SocketAddress votedFor = new SocketAddress();

    public RaftPersistentConfiguration(final String filename)
    {
        file = new File(filename);
        tmpFile = new File(filename + ".tmp");
        path = Paths.get(filename);
        tmpPath = Paths.get(filename + ".tmp");

        load();
    }

    public void delete()
    {
        file.delete();
        tmpFile.delete();
    }

    @Override
    public int getTerm()
    {
        return configuration.getTerm();
    }

    @Override
    public RaftPersistentConfiguration setTerm(final int term)
    {
        configuration.setTerm(term);
        return this;
    }

    @Override
    public SocketAddress getVotedFor()
    {
        if (votedFor.hostLength() > 0)
        {
            return votedFor;
        }
        else
        {
            return null;
        }
    }

    @Override
    public RaftPersistentConfiguration setVotedFor(final SocketAddress votedFor)
    {
        configuration.setVotedFor(votedFor);

        if (votedFor != null)
        {
            this.votedFor.wrap(votedFor);
        }
        else
        {
            this.votedFor.reset();
        }

        return this;
    }

    public List<SocketAddress> getMembers()
    {
        final List<SocketAddress> members = new ArrayList<>();

        final Iterator<RaftConfigurationMetadataMember> iterator = configuration.membersProp.iterator();
        while (iterator.hasNext())
        {
            final RaftConfigurationMetadataMember member = iterator.next();
            final DirectBuffer hostBuffer = member.getHost();

            final SocketAddress socketAddress = new SocketAddress();
            socketAddress.host(hostBuffer, 0, hostBuffer.capacity());
            socketAddress.setPort(member.getPort());

            members.add(socketAddress);
        }

        return members;
    }

    public RaftPersistentConfiguration setMembers(List<SocketAddress> members)
    {
        for (SocketAddress socketAddress : members)
        {
            addMember(socketAddress);
        }
        return this;
    }

    @Override
    public RaftPersistentConfiguration addMember(final SocketAddress member)
    {
        configuration.addMember(member);

        return this;
    }

    @Override
    public RaftPersistentStorage removeMember(SocketAddress member)
    {
        configuration.removeMember(member);

        return this;
    }

    @Override
    public RaftPersistentStorage clearMembers()
    {
        configuration.membersProp.reset();

        return this;
    }

    private void load()
    {
        if (file.exists())
        {
            final long length = file.length();
            if (length > buffer.capacity())
            {
                allocateBuffer((int) length);
            }

            try (InputStream is = new FileInputStream(file))
            {
                StreamUtil.read(is, buffer.byteArray());
            }
            catch (final IOException e)
            {
                throw new RuntimeException("Unable to read raft storage", e);
            }

            configuration.wrap(buffer);
            configuration.getVotedFor(votedFor);
        }
    }

    @Override
    public RaftPersistentConfiguration save()
    {
        final int length = configuration.getEncodedLength();

        if (length > buffer.capacity())
        {
            allocateBuffer(length);
        }

        configuration.write(buffer, 0);

        try (FileOutputStream os = new FileOutputStream(tmpFile))
        {
            os.write(buffer.byteArray(), 0, length);
            os.flush();
        }
        catch (final IOException e)
        {
            throw new RuntimeException("Unable to write raft storage", e);
        }

        try
        {
            try
            {
                Files.move(tmpPath, path, ATOMIC_MOVE);
            }
            catch (final Exception e)
            {
                // failed with atomic move, lets try again with normal replace move
                Files.move(tmpPath, path, REPLACE_EXISTING);
            }
        }
        catch (final IOException e)
        {
            throw new RuntimeException("Unable to replace raft storage", e);
        }

        return this;
    }

    private void allocateBuffer(final int capacity)
    {
        buffer.wrap(new byte[capacity]);
    }

    public DirectBuffer getTopicName()
    {
        return configuration.getTopicName();
    }

    public int getPartitionId()
    {
        return configuration.getPartitionId();
    }

    public int getReplicationFactor()
    {
        return configuration.getReplicationFactor();
    }

    public String getLogDirectory()
    {
        return configuration.getLogDirectory();
    }

    public RaftPersistentConfiguration setTopicName(DirectBuffer topicName)
    {
        configuration.setTopicName(topicName);
        return this;
    }

    public RaftPersistentConfiguration setPartitionId(int partitionId)
    {
        configuration.setPartitionId(partitionId);
        return this;
    }

    public RaftPersistentConfiguration setReplicationFactor(int replicationFactor)
    {
        configuration.setReplicationFactor(replicationFactor);
        return this;
    }

    public RaftPersistentConfiguration setLogDirectory(final String logDirectory)
    {
        configuration.setLogDirectory(logDirectory);
        return this;
    }
}
