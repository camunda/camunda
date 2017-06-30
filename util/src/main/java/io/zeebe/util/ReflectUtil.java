package io.zeebe.util;

public class ReflectUtil
{

    public static <T> T newInstance(Class<T> clazz)
    {
        try
        {
            return clazz.newInstance();
        }
        catch (Exception e)
        {
            throw new RuntimeException("Could not instantiate class " + clazz.getName(), e);
        }
    }
}
