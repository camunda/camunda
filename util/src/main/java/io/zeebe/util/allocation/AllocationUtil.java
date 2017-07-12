package io.zeebe.util.allocation;

import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.methodType;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Objects;

import io.zeebe.util.Loggers;
import org.slf4j.Logger;

/**
 * Implementation based on MMapDirectory of lucene-solr project.
 */
public class AllocationUtil
{
    private static final Logger LOG = Loggers.IO_LOGGER;

    public static void freeDirectBuffer(ByteBuffer directByteBuffer)
    {
        if (UNMAP_SUPPORTED)
        {
            try
            {
                CLEANER.freeBuffer(directByteBuffer);
            }
            catch (IOException e)
            {
                LOG.warn("Failed to free buffer.", e);
            }
        }
        else
        {
            LOG.warn("Unable to free buffer: {}", UNMAP_NOT_SUPPORTED_REASON);
        }
    }

    @FunctionalInterface
    private interface BufferCleaner
    {
        void freeBuffer(ByteBuffer b) throws IOException;
    }

    /**
     * <code>true</code>, if this platform supports unmapping mmapped files.
     */
    public static final boolean UNMAP_SUPPORTED;

    /**
     * if {@link #UNMAP_SUPPORTED} is {@code false}, this contains the reason
     * why unmapping is not supported.
     */
    public static final String UNMAP_NOT_SUPPORTED_REASON;

    /**
     * Reference to a BufferCleaner that does unmapping; {@code null} if not
     * supported.
     */
    private static final BufferCleaner CLEANER;

    static
    {
        final Object hack = AccessController.doPrivileged((PrivilegedAction<Object>) AllocationUtil::unmapHackImpl);
        if (hack instanceof BufferCleaner)
        {
            CLEANER = (BufferCleaner) hack;
            UNMAP_SUPPORTED = true;
            UNMAP_NOT_SUPPORTED_REASON = null;
        }
        else
        {
            CLEANER = null;
            UNMAP_SUPPORTED = false;
            UNMAP_NOT_SUPPORTED_REASON = hack.toString();
        }
    }

    private static Object unmapHackImpl()
    {
        final Lookup lookup = lookup();
        try
        {
            try
            {
                // *** sun.misc.Unsafe unmapping (Java 9+) ***
                final Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                // first check if Unsafe has the right method, otherwise we can
                // give up
                // without doing any security critical stuff:
                final MethodHandle unmapper = lookup.findVirtual(unsafeClass, "invokeCleaner", methodType(void.class, ByteBuffer.class));
                // fetch the unsafe instance and bind it to the virtual MH:
                final Field f = unsafeClass.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                final Object theUnsafe = f.get(null);
                return newBufferCleaner(ByteBuffer.class, unmapper.bindTo(theUnsafe));
            }
            catch (SecurityException se)
            {
                // rethrow to report errors correctly (we need to catch it here,
                // as we also catch RuntimeException below!):
                throw se;
            }
            catch (ReflectiveOperationException | RuntimeException e)
            {
                // *** sun.misc.Cleaner unmapping (Java 8) ***
                final Class<?> directBufferClass = Class.forName("java.nio.DirectByteBuffer");

                final Method m = directBufferClass.getMethod("cleaner");
                m.setAccessible(true);
                final MethodHandle directBufferCleanerMethod = lookup.unreflect(m);
                final Class<?> cleanerClass = directBufferCleanerMethod.type().returnType();

                /*
                 * "Compile" a MH that basically is equivalent to the following
                 * code: void unmapper(ByteBuffer byteBuffer) { sun.misc.Cleaner
                 * cleaner = ((java.nio.DirectByteBuffer) byteBuffer).cleaner();
                 * if (Objects.nonNull(cleaner)) { cleaner.clean(); } else {
                 * noop(cleaner); // the noop is needed because
                 * MethodHandles#guardWithTest always needs ELSE } }
                 */
                final MethodHandle cleanMethod = lookup.findVirtual(cleanerClass, "clean", methodType(void.class));
                final MethodHandle nonNullTest = lookup
                        .findStatic(Objects.class, "nonNull", methodType(boolean.class, Object.class))
                        .asType(methodType(boolean.class, cleanerClass));
                final MethodHandle noop = dropArguments(constant(Void.class, null).asType(methodType(void.class)), 0, cleanerClass);
                final MethodHandle unmapper = filterReturnValue(directBufferCleanerMethod, guardWithTest(nonNullTest, cleanMethod, noop))
                        .asType(methodType(void.class, ByteBuffer.class));
                return newBufferCleaner(directBufferClass, unmapper);
            }
        }
        catch (SecurityException se)
        {
            return "Unmapping is not supported, because not all required permissions are given to file: " + se +
                    " [Please grant at least the following permissions: RuntimePermission(\"accessClassInPackage.sun.misc\") " +
                    " and ReflectPermission(\"suppressAccessChecks\")]";
        }
        catch (ReflectiveOperationException | RuntimeException e)
        {
            return "Unmapping is not supported on this platform, because internal Java APIs are not compatible: " + e;
        }
    }

    private static BufferCleaner newBufferCleaner(final Class<?> unmappableBufferClass, final MethodHandle unmapper)
    {
        final MethodType methodType = methodType(void.class, ByteBuffer.class);
        assert Objects.equals(methodType, unmapper.type());
        return (ByteBuffer buffer) ->
        {
            if (!buffer.isDirect())
            {
                throw new IllegalArgumentException("unmapping only works with direct buffers");
            }
            if (!unmappableBufferClass.isInstance(buffer))
            {
                throw new IllegalArgumentException("buffer is not an instance of " + unmappableBufferClass.getName());
            }
            final Throwable error = AccessController.doPrivileged((PrivilegedAction<Throwable>) () ->
            {
                try
                {
                    unmapper.invokeExact(buffer);
                    return null;
                }
                catch (Throwable t)
                {
                    return t;
                }
            });
            if (error != null)
            {
                throw new IOException("Unable to unmap the mapped buffer.", error);
            }
        };
    }

}
