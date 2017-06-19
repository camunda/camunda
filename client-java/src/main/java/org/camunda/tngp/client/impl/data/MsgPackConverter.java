/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.client.impl.data;

import static org.camunda.tngp.util.StringUtil.getBytes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public class MsgPackConverter
{
    protected static final JsonEncoding JSON_ENCODING = JsonEncoding.UTF8;
    protected static final Charset JSON_CHARSET = StandardCharsets.UTF_8;

    protected final JsonFactory msgPackFactory = new MessagePackFactory();
    protected final JsonFactory jsonFactory = new MappingJsonFactory();

    public byte[] convertToMsgPack(String json)
    {
        final byte[] jsonBytes = getBytes(json, JSON_CHARSET);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(jsonBytes);
        return convertToMsgPack(inputStream);
    }

    public byte[] convertToMsgPack(final InputStream inputStream)
    {
        try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream())
        {
            convert(inputStream, outputStream, jsonFactory, msgPackFactory);

            return outputStream.toByteArray();
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to convert JSON to MessagePack", e);
        }
    }

    public String convertToJson(byte[] msgPack)
    {
        final byte[] jsonBytes = convertToJsonBytes(msgPack);
        return new String(jsonBytes, JSON_CHARSET);
    }

    public InputStream convertToJsonInputStream(byte[] msgPack)
    {
        final byte[] jsonBytes = convertToJsonBytes(msgPack);
        return new ByteArrayInputStream(jsonBytes);
    }

    protected byte[] convertToJsonBytes(byte[] msgPack)
    {
        final InputStream inputStream = new ByteArrayInputStream(msgPack);

        try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream())
        {
            convert(inputStream, outputStream, msgPackFactory, jsonFactory);

            final byte[] jsonBytes = outputStream.toByteArray();
            return jsonBytes;
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to convert MessagePack to JSON", e);
        }
    }

    protected void convert(InputStream in, OutputStream out, JsonFactory inFormat, JsonFactory outFormat) throws Exception
    {
        final JsonParser parser = inFormat.createParser(in);
        final JsonGenerator generator = outFormat.createGenerator(out, JSON_ENCODING);
        final JsonToken token = parser.nextToken();
        if (token != JsonToken.START_OBJECT && token != JsonToken.START_ARRAY && token != JsonToken.VALUE_NULL)
        {
            throw new RuntimeException("Document does not begin with an object or array");
        }

        generator.copyCurrentStructure(parser);

        if (parser.nextToken() != null)
        {
            throw new RuntimeException("Document has more content than a single object/array");
        }

        generator.flush();
    }

}
