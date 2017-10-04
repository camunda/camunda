package io.zeebe.broker.system.log;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.protocol.Protocol;

public class PartitionIdGenerator extends UnpackedObject
{
    protected IntegerProperty id = new IntegerProperty("id", Protocol.SYSTEM_PARTITION + 1);

    public PartitionIdGenerator()
    {
        declareProperty(id);
    }

    public int currentId()
    {
        return currentId(0);
    }

    public int currentId(int offset)
    {
        return this.id.getValue() + offset;
    }

    public void moveToNextId()
    {
        moveToNextIds(1);
    }

    public void moveToNextIds(int offset)
    {
        final int id = this.id.getValue();
        this.id.setValue(id + offset);
    }

}
