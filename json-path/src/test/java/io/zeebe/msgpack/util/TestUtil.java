package io.zeebe.msgpack.util;

import io.zeebe.msgpack.query.MsgPackFilterContext;

public class TestUtil
{

    public static MsgPackFilterContext generateDefaultInstances(int... filterIds)
    {

        final MsgPackFilterContext filterInstances = new MsgPackFilterContext(filterIds.length, 10);
        for (int i = 0; i < filterIds.length; i++)
        {
            filterInstances.appendElement();
            filterInstances.filterId(filterIds[i]);
        }
        return filterInstances;
    }


}
