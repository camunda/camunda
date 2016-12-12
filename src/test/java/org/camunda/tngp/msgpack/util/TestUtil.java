package org.camunda.tngp.msgpack.util;

import org.camunda.tngp.msgpack.query.MsgPackFilterContext;

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
