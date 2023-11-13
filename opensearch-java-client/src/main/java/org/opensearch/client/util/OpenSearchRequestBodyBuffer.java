/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.client.util;

import jakarta.json.stream.JsonGenerator;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.NdJsonpSerializable;
import org.opensearch.client.transport.OpenSearchTransport;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.zip.GZIPOutputStream;

/**
 * Serializes and captures an OpenSearch request body, and then provides access to it in convenient
 * forms for HTTP requests.  This is a utility class for use by various {@link OpenSearchTransport}
 * implementations.
 * <P>
 *     Request bodies can be automatically compressed when they exceed a given size.
 * </P>
 */
public class OpenSearchRequestBodyBuffer {
    private static final byte[] NO_BYTES = new byte[0];
    private final OutputBuffer outputBuffer;
    private final CompressingOutputBuffer captureBuffer;
    private final JsonpMapper mapper;
    private final JsonGenerator jsonGenerator;
    private boolean hasContent = false;
    private boolean isMulti = false;
    private boolean isClosed = false;
    private byte[] arrayMemo = null;

    /**
     * Create a request body buffer
     *
     * @param mapper                 mapper used to serialize the content
     * @param requestCompressionSize When the captured data exceeds this size, it will be automatically
     *                               compressed.  Pass Integer.MAX_VALUE to prevent compression
     */
    public OpenSearchRequestBodyBuffer(JsonpMapper mapper, int requestCompressionSize) {
        this.outputBuffer = new OutputBuffer();
        this.captureBuffer = new CompressingOutputBuffer(this.outputBuffer, requestCompressionSize);
        this.mapper = mapper;
        jsonGenerator = mapper.jsonProvider().createGenerator(this.captureBuffer);
    }

    /**
     * Add some content to the buffer.  If the buffer already contains some data, or if the provided
     * object implements {@link NdJsonpSerializable}, then the buffer will contain multiple objects
     * in newline-delimited JSON format.
     *
     * @param content The new content object to add
     */
    public void addContent(Object content) throws IOException {
        if (hasContent && !isMulti) {
            captureBuffer.write((byte) '\n');
            isMulti = true;
        }
        hasContent = true;
        if (content instanceof NdJsonpSerializable) {
            isMulti = true;
            addNdJson(((NdJsonpSerializable) content));
        } else {
            mapper.serialize(content, jsonGenerator);
            jsonGenerator.flush();
            if (isMulti) {
                captureBuffer.write((byte) '\n');
            }
        }
    }

    private void addNdJson(NdJsonpSerializable content) throws IOException {
        Iterator<?> values = content._serializables();
        while (values.hasNext()) {
            Object value = values.next();
            if (value instanceof NdJsonpSerializable && value != content) {
                addNdJson((NdJsonpSerializable) value);
            } else {
                hasContent = true;
                mapper.serialize(value, jsonGenerator);
                jsonGenerator.flush();
                captureBuffer.write((byte) '\n');
            }
        }
    }

    /**
     * @return true if the content has been compressed
     */
    public boolean isCompressed() {
        return captureBuffer.isCompressed();
    }

    /**
     * @return true if this buffer contains multiple newline-delimited objects.
     */
    public boolean isNdJson() {
        return isMulti;
    }

    /**
     * Get the value of the Content-Encoding header that should be sent along with this buffer,
     * or null if there shouldn't be one.
     */
    @CheckForNull
    public String getContentEncoding() {
        if (captureBuffer.isCompressed()) {
            return "gzip";
        }
        return null;
    }

    /**
     * Get the value of the Content-Type header that should be sent along with this buffer.
     */
    @Nonnull
    public String getContentType() {
        return "application/json";
    }

    /**
     * Get the value of the Content-Length header that should be sent along with this buffer.
     * <p>
     * This call finalizes the buffer.  After this call, any attempt to add more content
     * will throw an IOException.
     * </P>
     *
     * @return The length of the buffered content
     */
    public long getContentLength() {
        ensureClosed();
        return outputBuffer.size();
    }

    /**
     * Get the contents of this buffer as a byte array.
     * <p>
     * This call finalizes the buffer.  After this call, any attempt to add more content
     * will throw an IOException.
     * </P>
     *
     * @return The buffered data
     */
    public byte[] getByteArray() {
        if (arrayMemo == null) {
            ensureClosed();
            arrayMemo = outputBuffer.size() <= 0 ? NO_BYTES : outputBuffer.toByteArray();
        }
        return arrayMemo;
    }

    /**
     * Get the contents of this buffer as a new InputStream.
     * <p>
     * Calls to this method are cheap, since all the new streams will share the same
     * underlying array
     * </P>
     * <p>
     * This call finalizes the buffer.  After this call, any attempt to add more content
     * will throw an IOException.
     * </P>
     *
     * @return The buffered data
     */
    public InputStream getInputStream() {
        ensureClosed();
        if (outputBuffer.size() <= 0) {
            return new ByteArrayInputStream(NO_BYTES);
        } else {
            return outputBuffer.toInputStream();
        }
    }

    /**
     * This call finalizes the buffer.  After this call, any attempt to add more content
     * will throw an IOException.
     *
     * @throws IOException
     */
    public void close() throws IOException {
        if (!isClosed) {
            isClosed = true;
            jsonGenerator.close();
            captureBuffer.close();
        }
    }

    private void ensureClosed() {
        try {
            close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class OutputBuffer extends ByteArrayOutputStream {
        InputStream toInputStream() {
            return new ByteArrayInputStream(this.buf, 0, this.count);
        }
    }

    private static class ClosedOutputBuffer extends OutputStream {
        static final ClosedOutputBuffer INSTANCE = new ClosedOutputBuffer();

        @Override
        public void write(int b) throws IOException {
            throw new IOException("write to closed stream");
        }

        @Override
        public void close() throws IOException {
        }
    }

    private static class CompressingOutputBuffer extends OutputStream {
        private final OutputBuffer outputBuffer;
        private final int requestCompressionSize;
        private OutputStream delegate;
        private int bytesUntilCompression;
        private boolean isCompressed;

        private CompressingOutputBuffer(OutputBuffer outputBuffer, int requestCompressionSize) {
            this.outputBuffer = outputBuffer;
            this.delegate = outputBuffer;
            this.requestCompressionSize = requestCompressionSize;
            this.bytesUntilCompression = requestCompressionSize;
            this.isCompressed = false;
        }

        public boolean isCompressed() {
            return isCompressed;
        }

        @Override
        public void write(byte[] b) throws IOException {
            if ((bytesUntilCompression -= b.length) < 0) {
                checkCompress();
            }
            delegate.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if ((bytesUntilCompression -= len) < 0) {
                checkCompress();
            }
            delegate.write(b, off, len);
        }

        @Override
        public void write(int b) throws IOException {
            if (--bytesUntilCompression < 0) {
                checkCompress();
            }
            delegate.write(b);
        }

        private void checkCompress() throws IOException {
            if (delegate == outputBuffer && requestCompressionSize < Integer.MAX_VALUE) {
                // prevent future checks
                this.bytesUntilCompression = Integer.MAX_VALUE;
                byte[] uncompressed = outputBuffer.toByteArray();
                outputBuffer.reset();
                delegate = new GZIPOutputStream(outputBuffer);
                if (uncompressed.length > 0) {
                    delegate.write(uncompressed);
                }
                isCompressed = true;
            }
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
            delegate = ClosedOutputBuffer.INSTANCE;
        }
    }
}
