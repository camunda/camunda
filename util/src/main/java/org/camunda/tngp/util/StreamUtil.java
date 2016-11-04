package org.camunda.tngp.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.agrona.BitUtil;

public class StreamUtil
{
    protected static final int DEFAULT_BUFFER_SIZE = 4 * 1024;
    protected static final byte[] DEFAULT_STREAM_BUFFER = new byte[DEFAULT_BUFFER_SIZE];

    public static MessageDigest getDigest(final String algorithm)
    {
        try
        {
            return MessageDigest.getInstance(algorithm);
        }
        catch (final NoSuchAlgorithmException e)
        {
            throw new IllegalStateException(e);
        }
    }

    public static MessageDigest getSha1Digest()
    {
        return getDigest("SHA1");
    }

    public static MessageDigest updateDigest(final MessageDigest messageDigest, final InputStream data) throws IOException
    {
        int n;
        while ((n = data.read(DEFAULT_STREAM_BUFFER)) > -1)
        {
            messageDigest.update(DEFAULT_STREAM_BUFFER, 0, n);
        }
        return messageDigest;
    }

    public static String sha1Hex(final InputStream data) throws IOException
    {
        final MessageDigest messageDigest = updateDigest(getSha1Digest(), data);
        return digestAsHex(messageDigest);
    }

    public static String digestAsHex(final MessageDigest messageDigest)
    {
        try
        {
            final byte[] digest = messageDigest.digest();
            final byte[] hexByteArray = BitUtil.toHexByteArray(digest);
            return new String(hexByteArray, "UTF-8");
        }
        catch (final UnsupportedEncodingException e)
        {
            throw new IllegalArgumentException(e);
        }
    }

    public static int copy(final InputStream input, final OutputStream output) throws IOException
    {
        int count = 0;
        int n;
        while ((n = input.read(DEFAULT_STREAM_BUFFER)) > -1)
        {
            output.write(DEFAULT_STREAM_BUFFER, 0, n);
            count += n;
        }
        return count;
    }

    public static void write(final File file, final String data) throws IOException
    {
        try (final FileOutputStream os = new FileOutputStream(file))
        {
            os.write(data.getBytes("UTF-8"));
        }
    }

    public static void write(final File file, final InputStream data, final MessageDigest messageDigest) throws IOException
    {
        try (final DigestOutputStream os = new DigestOutputStream(new FileOutputStream(file), messageDigest))
        {
            copy(data, os);
        }

        final String digest = digestAsHex(messageDigest);
        final String fileName = file.getName();

        final String content = String.format("%s %s", digest, fileName);

        final String algorithm = messageDigest.getAlgorithm().toLowerCase();
        final String targetFileName = String.format("%s.%s", file.getAbsolutePath(), algorithm);

        write(new File(targetFileName), content);
    }

    public static boolean canRead(final File file, final MessageDigest messageDigest)
    {
        boolean isReadable = false;

        final File checksum = new File(file.getAbsolutePath() + "." + messageDigest.getAlgorithm().toLowerCase());

        if (file.exists() && checksum.exists())
        {
            String checksumDigest = null;
            String checksumFileName = null;

            try (InputStream is = new FileInputStream(checksum))
            {
                final byte[] data = new byte[(int) checksum.length()];
                read(is, data);
                final String content = new String(data, "UTF-8");
                final String[] parts = content.split(" ");
                checksumDigest = parts[0];
                checksumFileName = parts[1];
            }
            catch (final IOException e)
            {
                // ignore
            }

            if (checksumFileName.equals(file.getName()))
            {
                try (InputStream is = new FileInputStream(file))
                {
                    updateDigest(messageDigest, is);
                }
                catch (final IOException e)
                {
                    // ignore
                }

                final String digest = digestAsHex(messageDigest);

                isReadable = digest.equals(checksumDigest);
            }
        }

        return isReadable;
    }

    public static int read(final InputStream input, final byte[] dst) throws IOException
    {
        final int length = dst.length;
        int remaining = length;

        while (remaining > 0)
        {
            final int location = length - remaining;
            final int count = input.read(dst, location, remaining);
            if (count == -1)
            {
                break;
            }
            remaining -= count;
        }

        return length - remaining;
    }
}
