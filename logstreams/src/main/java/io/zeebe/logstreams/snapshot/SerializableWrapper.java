package io.zeebe.logstreams.snapshot;

import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;

import io.zeebe.logstreams.spi.SnapshotSupport;

/**
 * Wraps a {@link Serializable} object providing {@link SnapshotSupport} to be used in a StreamProcessor.
 *<p>
 * <strong>NOTE</strong>: obviously all the known caveats around java serialization apply to this class.
 * Or on other words: do not use this outside of demo applications.
 *
 * @param <T> the type of the wrapped {@link Serializable}.
 */
public class SerializableWrapper<T extends Serializable> implements SnapshotSupport
{
    protected T object;

    public SerializableWrapper(T object)
    {
        this.object = object;
    }

    public T getObject()
    {
        return object;
    }

    @Override
    public void writeSnapshot(OutputStream outputStream) throws Exception
    {
        final ObjectOutputStream oos = new ObjectOutputStream(outputStream);
        oos.writeObject(object);
        oos.flush();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void recoverFromSnapshot(InputStream inputStream) throws Exception
    {
        final ObjectInputStream ois = new ObjectInputStream(inputStream);
        object = (T) ois.readObject();
    }

    @Override
    public void reset()
    {
        // do nothing
    }
}
