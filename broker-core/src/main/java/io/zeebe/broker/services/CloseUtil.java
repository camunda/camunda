package io.zeebe.broker.services;

import java.io.Closeable;

public class CloseUtil
{

    public static void closeSilently(Closeable closeable)
    {
        if (closeable != null)
        {
            try
            {
                closeable.close();
            }
            catch (Exception e)
            {
                // ignore
            }
        }
    }

}
