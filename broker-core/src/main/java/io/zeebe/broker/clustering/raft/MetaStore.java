package io.zeebe.broker.clustering.raft;

import static io.zeebe.util.StringUtil.fromBytes;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.broker.clustering.raft.entry.ConfiguredMember;
import io.zeebe.broker.util.msgpack.UnpackedObject;
import io.zeebe.broker.util.msgpack.property.ArrayProperty;
import io.zeebe.broker.util.msgpack.property.IntegerProperty;
import io.zeebe.broker.util.msgpack.property.LongProperty;
import io.zeebe.broker.util.msgpack.property.StringProperty;
import io.zeebe.broker.util.msgpack.value.ArrayValue;
import io.zeebe.msgpack.spec.MsgPackHelper;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.LangUtil;
import io.zeebe.util.StreamUtil;

public class MetaStore
{
    private static final DirectBuffer EMPTY_ARRAY = new UnsafeBuffer(MsgPackHelper.EMPTY_ARRAY);
    private static final DirectBuffer EMPTY_STRING = new UnsafeBuffer(0, 0);

    protected final String file;

    protected final Meta meta = new Meta();
    protected final SocketAddress voted = new SocketAddress();

    protected final UnsafeBuffer readBuffer;
    protected final UnsafeBuffer writeBuffer;

    public MetaStore(final String file)
    {
        this.file = file;

        this.writeBuffer = new UnsafeBuffer(0, 0);
        writeBuffer.wrap(new byte[1024]);

        this.readBuffer = new UnsafeBuffer(0, 0);
        readBuffer.wrap(new byte[1024]);
    }

    public DirectBuffer loadTopicName()
    {
        load();
        return meta.topicNameProp.getValue();
    }

    public int loadPartitionId()
    {
        load();
        return meta.partitionIdProp.getValue();
    }

    public String loadLogDirectory()
    {
        load();

        final DirectBuffer buffer = meta.directoryProp.getValue();
        final int length = buffer.capacity();

        final byte[] tmp = new byte[length];
        buffer.getBytes(0, tmp, 0, length);

        return fromBytes(tmp);
    }

    public MetaStore storeTopicNameAndPartitionIdAndDirectory(final DirectBuffer topicName, final int partitionId, final String directory)
    {
        meta.topicNameProp.setValue(topicName);
        meta.partitionIdProp.setValue(partitionId);
        meta.directoryProp.setValue(directory);
        store();
        return this;
    }

    public int loadTerm()
    {
        load();
        return meta.termProp.getValue();
    }

    public MetaStore storeTerm(final int term)
    {
        meta.termProp.setValue(term);
        store();
        return this;
    }

    public SocketAddress loadVote()
    {
        load();

        final StringProperty voteHostProp = meta.voteHostProp;
        final IntegerProperty votePortProp = meta.votePortProp;

        final DirectBuffer hostValue = voteHostProp.getValue();
        final int hostLength = hostValue.capacity();

        if (hostLength > 0)
        {
            voted.host(hostValue, 0, hostLength);
            voted.port(votePortProp.getValue());
            return voted;
        }

        return null;
    }

    public MetaStore storeVote(final SocketAddress vote)
    {
        if (vote != null)
        {
            meta.voteHostProp.setValue(vote.getHostBuffer(), 0, vote.hostLength());
            meta.votePortProp.setValue(vote.port());
        }
        else
        {
            meta.voteHostProp.setValue(EMPTY_STRING, 0, 0);
            meta.votePortProp.setValue(-1);
        }

        store();

        return this;
    }

    public MetaStore storeTermAndVote(final int term, final SocketAddress vote)
    {
        meta.termProp.setValue(term);

        if (vote != null)
        {
            meta.voteHostProp.setValue(vote.getHostBuffer(), 0, vote.hostLength());
            meta.votePortProp.setValue(vote.port());
        }
        else
        {
            meta.voteHostProp.setValue(EMPTY_STRING, 0, 0);
            meta.votePortProp.setValue(-1);
        }

        store();

        return this;
    }

    public Configuration loadConfiguration()
    {
        load();

        final int configEntryTerm = meta.configEntryTermProp.getValue();
        final long configEntryPosition = meta.configEntryPositionProp.getValue();

        final List<Member> members = new CopyOnWriteArrayList<>();
        final ArrayProperty<ConfiguredMember> iterator = meta.membersProp;

        if (!iterator.hasNext())
        {
            return null;
        }

        while (iterator.hasNext())
        {
            final ConfiguredMember m = iterator.next();

            final DirectBuffer hostValue = m.getHost();
            final int hostLength = hostValue.capacity();
            final int port = m.getPort();

            final SocketAddress endpoint = new SocketAddress();
            endpoint.port(port);
            endpoint.host(hostValue, 0, hostValue.capacity());

            final Member member = new Member();
            member.endpoint()
                .port(port)
                .host(hostValue, 0, hostLength);
            members.add(member);
        }

        return new Configuration(configEntryPosition, configEntryTerm, members);
    }

    public MetaStore storeConfiguration(final Configuration configuration)
    {
        if (configuration == null)
        {
            throw new IllegalArgumentException();
        }

        meta.configEntryTermProp.setValue(configuration.configurationEntryTerm());
        meta.configEntryPositionProp.setValue(configuration.configurationEntryPosition());

        meta.membersProp.reset();
        final ArrayProperty<ConfiguredMember> iterator = meta.membersProp;
        final List<Member> members = configuration.members();

        for (int i = 0; i < members.size(); i++)
        {
            final Member m = members.get(i);
            final SocketAddress endpoint = m.endpoint();

            final MutableDirectBuffer hostValue = endpoint.getHostBuffer();
            final int hostLength = endpoint.hostLength();

            iterator.add()
                .setHost(hostValue, 0, hostLength)
                .setPort(endpoint.port());
        }

        store();

        return this;
    }

    protected void load()
    {
        final File metaFile = new File(file);

        if (metaFile.exists())
        {
            final long length = metaFile.length();
            if (length > readBuffer.capacity())
            {
                readBuffer.wrap(new byte[(int) length]);
            }

            try (InputStream is = new FileInputStream(metaFile))
            {
                StreamUtil.read(is, readBuffer.byteArray());
            }
            catch (final IOException e)
            {
                LangUtil.rethrowUnchecked(e);
            }

            meta.wrap(readBuffer);
        }
    }

    protected void store()
    {
        final int length = meta.getEncodedLength();

        if (length > writeBuffer.capacity())
        {
            writeBuffer.wrap(new byte[length]);
        }

        meta.write(writeBuffer, 0);

        final File target = new File(String.format("%s.bak", file));
        try (OutputStream os = new FileOutputStream(target))
        {
            os.write(writeBuffer.byteArray(), 0, length);
            os.flush();

        }
        catch (final IOException e)
        {
            LangUtil.rethrowUnchecked(e);
        }

        try
        {
            final Path sourcePath = Paths.get(String.format("%s", file));
            final Path backupPath = Paths.get(String.format("%s.bak", file));
            Files.move(backupPath, sourcePath, REPLACE_EXISTING);
        }
        catch (final IOException e)
        {
            LangUtil.rethrowUnchecked(e);
        }
    }

    private static class Meta extends UnpackedObject
    {
        protected StringProperty topicNameProp = new StringProperty("topicName", "");
        protected IntegerProperty partitionIdProp = new IntegerProperty("partitionId", -1);
        protected StringProperty directoryProp = new StringProperty("logDirectory", "");
        protected IntegerProperty termProp = new IntegerProperty("term", 0);
        protected StringProperty voteHostProp = new StringProperty("voteHost", "");
        protected IntegerProperty votePortProp = new IntegerProperty("votePort", 0);

        protected IntegerProperty configEntryTermProp = new IntegerProperty("configurationEntryTerm", 0);
        protected LongProperty configEntryPositionProp = new LongProperty("configurationEntryPosition", 0L);

        protected ConfiguredMember configuredMember = new ConfiguredMember();
        protected ArrayProperty<ConfiguredMember> membersProp = new ArrayProperty<>("members",
                new ArrayValue<>(),
                new ArrayValue<>(EMPTY_ARRAY, 0, EMPTY_ARRAY.capacity()),
                configuredMember);

        Meta()
        {
            declareProperty(partitionIdProp);
            declareProperty(topicNameProp);
            declareProperty(directoryProp);
            declareProperty(termProp);
            declareProperty(voteHostProp);
            declareProperty(votePortProp);
            declareProperty(configEntryTermProp);
            declareProperty(configEntryPositionProp);
            declareProperty(membersProp);
        }
    }

}
