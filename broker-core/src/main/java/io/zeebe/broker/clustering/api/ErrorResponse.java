/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.clustering.api;

import static io.zeebe.clustering.management.ErrorResponseEncoder.dataCharacterEncoding;
import static io.zeebe.clustering.management.ErrorResponseEncoder.dataHeaderLength;

import java.io.UnsupportedEncodingException;

import io.zeebe.broker.util.SbeBufferWriterReader;
import io.zeebe.clustering.management.ErrorResponseCode;
import io.zeebe.clustering.management.ErrorResponseDecoder;
import io.zeebe.clustering.management.ErrorResponseEncoder;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.LangUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.ArrayUtil;
import org.agrona.concurrent.UnsafeBuffer;

public class ErrorResponse extends SbeBufferWriterReader<ErrorResponseEncoder, ErrorResponseDecoder>
{
    private final ErrorResponseEncoder bodyEncoder = new ErrorResponseEncoder();
    private final ErrorResponseDecoder bodyDecoder = new ErrorResponseDecoder();

    private ErrorResponseCode code = ErrorResponseCode.NULL_VAL;
    private final DirectBuffer data = new UnsafeBuffer();

    @Override
    public void reset()
    {
        super.reset();
        data.wrap(ArrayUtil.EMPTY_BYTE_ARRAY);
        code = ErrorResponseCode.NULL_VAL;
    }

    public ErrorResponse setCode(ErrorResponseCode code)
    {
        this.code = code;
        return this;
    }

    public ErrorResponseCode getCode()
    {
        return code;
    }

    public ErrorResponse setData(final String data)
    {
        try
        {
            final byte[] encoded = data.getBytes(dataCharacterEncoding());
            this.data.wrap(encoded);
        }
        catch (final UnsupportedEncodingException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return this;
    }

    public DirectBuffer getData()
    {
        return data;
    }

    public String getMessage()
    {
        return BufferUtil.bufferAsString(data);
    }

    @Override
    protected ErrorResponseEncoder getBodyEncoder()
    {
        return bodyEncoder;
    }

    @Override
    protected ErrorResponseDecoder getBodyDecoder()
    {
        return bodyDecoder;
    }

    @Override
    public int getLength()
    {
        return super.getLength() + dataHeaderLength() + data.capacity();
    }

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        super.wrap(buffer, offset, length);
        code = bodyDecoder.code();
        data.wrap(buffer, bodyDecoder.limit() + dataHeaderLength(), bodyDecoder.dataLength());
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        super.write(buffer, offset);
        bodyEncoder.code(code);
        bodyEncoder.putData(data, 0, data.capacity());
    }
}
