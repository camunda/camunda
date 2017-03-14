package org.camunda.tngp.test.broker.protocol.clientapi;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class MapBuilder<T>
{
    protected T returnValue;
    protected Consumer<Map<String, Object>> mapCallback;

    protected Map<String, Object> map;

    public MapBuilder(T returnValue, Consumer<Map<String, Object>> mapCallback)
    {
        this.returnValue = returnValue;
        this.mapCallback = mapCallback;
        this.map = new HashMap<>();
    }

    public MapBuilder<T> put(String key, Object value)
    {
        this.map.put(key, value);
        return this;
    }

    public T done()
    {
        mapCallback.accept(map);
        return returnValue;
    }

}
