package org.camunda.tngp.broker.util.msgpack.value;

import java.util.ArrayList;
import java.util.List;

import org.camunda.tngp.broker.util.msgpack.property.BaseProperty;
import org.camunda.tngp.broker.util.msgpack.property.UndeclaredProperty;
import org.camunda.tngp.msgpack.spec.MsgPackReader;
import org.camunda.tngp.msgpack.spec.MsgPackWriter;

public class ObjectValue extends BaseValue
{
    private final List<BaseProperty<? extends BaseValue>> declaredProperties = new ArrayList<>();
    private final List<BaseProperty<? extends BaseValue>> properties = new ArrayList<>();
    private final List<UndeclaredProperty> recycledProperties = new ArrayList<>();

    private final StringValue decodedKey = new StringValue();

    public ObjectValue declareProperty(BaseProperty<? extends BaseValue> prop)
    {
        declaredProperties.add(prop);
        prop.init(this);
        return this;
    }

    @Override
    public void reset()
    {
        for (int i = properties.size() - 1; i >= 0; --i)
        {
            final BaseProperty<? extends BaseValue> prop = properties.get(i);
            prop.reset();
            tryRecycle(prop);
            properties.remove(i);
        }
    }

    private void tryRecycle(BaseProperty<? extends BaseValue> prop)
    {
        if (prop instanceof UndeclaredProperty)
        {
            recycledProperties.add((UndeclaredProperty) prop);
        }
    }

    private BaseProperty<PackedValue> getRecycledProperty()
    {
        final int recycledSize = recycledProperties.size();

        BaseProperty<PackedValue> prop = null;

        if (recycledSize > 0)
        {
            prop = recycledProperties.remove(recycledSize - 1);
        }
        else
        {
            prop = new UndeclaredProperty();
            prop.init(this);
        }

        return prop;
    }

    @Override
    public void writeJSON(StringBuilder builder)
    {
        builder.append("{");

        for (int i = 0; i < properties.size(); i++)
        {
            if (i > 0)
            {
                builder.append(",");
            }

            final BaseProperty<? extends BaseValue> prop = properties.get(i);

            if (prop.isSet())
            {
                prop.getKey().writeJSON(builder);
                builder.append(":");
                prop.getPropertyValue().writeJSON(builder);
            }
        }

        builder.append("}");
    }

    @Override
    public void read(MsgPackReader reader)
    {
        final int mapSize = reader.readMapHeader();

        for (int i = 0; i < mapSize; ++i)
        {
            decodedKey.read(reader);

            BaseProperty<? extends BaseValue> prop = null;

            for (int k = 0; k < declaredProperties.size(); ++k)
            {
                final BaseProperty<? extends BaseValue> declaredProperty = declaredProperties.get(k);
                final StringValue declaredKey = declaredProperty.getKey();

                if (declaredKey.equals(decodedKey))
                {
                    prop = declaredProperty;
                    break;
                }
            }

            if (prop == null)
            {
                prop = getRecycledProperty();
                prop.getKey().wrap(decodedKey);
            }

            final BaseValue value = prop.getPropertyValue();
            value.read(reader);
            prop.set();
        }
    }

    @Override
    public void write(MsgPackWriter writer)
    {
        final int size = properties.size();

        writer.writeMapHeader(size);

        for (int i = 0; i < size; ++i)
        {
            final BaseProperty<? extends BaseValue> prop = properties.get(i);
            final StringValue key = prop.getKey();
            final BaseValue value = prop.getPropertyValue();

            key.write(writer);
            value.write(writer);
        }
    }

    @Override
    public int getEncodedLength()
    {
        final int size = properties.size();

        int length = MsgPackWriter.getEncodedMapHeaderLenght(size);

        for (int i = 0; i < size; ++i)
        {
            final BaseProperty<? extends BaseValue> prop = properties.get(i);
            final StringValue key = prop.getKey();
            final BaseValue value = prop.getPropertyValue();

            length += key.getEncodedLength();
            length += value.getEncodedLength();
        }

        return length;
    }

    public List<BaseProperty<? extends BaseValue>> getProperties()
    {
        return properties;
    }

    public List<BaseProperty<? extends BaseValue>> getDeclaredProperties()
    {
        return declaredProperties;
    }

    public void addProperty(BaseProperty<? extends BaseValue> objectProperty)
    {
        properties.add(objectProperty);
    }

    public void removePoperty(BaseProperty<? extends BaseValue> objectProperty)
    {
        properties.remove(objectProperty);
    }

}
