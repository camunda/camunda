package io.zeebe.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;


public final class StringUtil
{

    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    public static byte[] getBytes(final String value)
    {
        return getBytes(value, DEFAULT_CHARSET);
    }

    public static byte[] getBytes(final String value, final Charset charset)
    {
        byte[] bytes = null;

        try
        {
            bytes = value.getBytes(charset);
        }
        catch (final Exception e)
        {
            LangUtil.rethrowUnchecked(e);
        }

        return bytes;
    }

    public static String fromBytes(final byte[] bytes)
    {
        return fromBytes(bytes, DEFAULT_CHARSET);
    }

    public static String fromBytes(final byte[] bytes, final Charset charset)
    {
        return new String(bytes, charset);
    }

}
