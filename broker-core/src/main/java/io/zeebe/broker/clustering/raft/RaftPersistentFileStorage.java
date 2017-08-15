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
package io.zeebe.broker.clustering.raft;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.raft.RaftPersistentStorage;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.StreamUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class RaftPersistentFileStorage implements RaftPersistentStorage
{

    private final RaftConfiguration configuration = new RaftConfiguration();


    private final File file;
    private final File tmpFile;
    private final Path path;
    private final Path tmpPath;

    private final MutableDirectBuffer buffer = new UnsafeBuffer(0, 0);

    private final SocketAddress votedFor = new SocketAddress();
    private LogStream logStream;

    public RaftPersistentFileStorage(final String filename)
    {
        file = new File(filename);
        tmpFile = new File(filename + ".tmp");
        path = Paths.get(filename);
        tmpPath = Paths.get(filename + ".tmp");

        load();
    }

    @Override
    public int getTerm()
    {
        return logStream.getTerm();
    }

    @Override
    public RaftPersistentFileStorage setTerm(final int term)
    {
        logStream.setTerm(term);

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
    public RaftPersistentFileStorage setVotedFor(final SocketAddress votedFor)
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

        while (configuration.membersProp.hasNext())
        {
            final RaftConfigurationMember member = configuration.membersProp.next();
            final DirectBuffer hostBuffer = member.getHost();

            final SocketAddress socketAddress = new SocketAddress();
            socketAddress.host(hostBuffer, 0, hostBuffer.capacity());
            socketAddress.setPort(member.getPort());

            members.add(socketAddress);
        }

        return members;
    }

    @Override
    public RaftPersistentFileStorage addMember(final SocketAddress member)
    {
        configuration.addMember(member);

        return this;
    }

    @Override
    public RaftPersistentFileStorage clearMembers()
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

    public RaftPersistentFileStorage save()
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

    public String getLogDirectory()
    {
        return configuration.getLogDirectory();
    }

    public RaftPersistentFileStorage setLogStream(final LogStream logStream)
    {
        this.logStream = logStream;

        configuration.setTopicName(logStream.getTopicName());
        configuration.setPartitionId(logStream.getPartitionId());

        logStream.setTerm(configuration.getTerm());

        return this;
    }

    public RaftPersistentFileStorage setLogDirectory(final String logDirectory)
    {
        configuration.setLogDirectory(logDirectory);

        return this;
    }
}
