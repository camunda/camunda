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
package io.zeebe.broker.logstreams.processor;

import com.fasterxml.jackson.databind.*;
import io.zeebe.logstreams.spi.ComposableSnapshotSupport;
import io.zeebe.util.ReflectUtil;
import java.io.*;

/**
 * Should be used for small data sets only, where programming convenience and human readability of
 * the target format is valued over performance optimization.
 */
public class JsonSnapshotSupport<T> implements ComposableSnapshotSupport {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final Class<T> dataType;
  private final ObjectWriter writer;
  private final ObjectReader reader;

  private final ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();

  private T data;

  public T getData() {
    return data;
  }

  public JsonSnapshotSupport(Class<T> type) {
    this.dataType = type;
    this.writer = OBJECT_MAPPER.writerFor(type).withDefaultPrettyPrinter();
    this.reader = OBJECT_MAPPER.readerFor(type);
    reset();
  }

  @Override
  public long writeSnapshot(OutputStream outputStream) throws Exception {
    final long size = outBuffer.size();

    outBuffer.writeTo(outputStream);
    outBuffer.reset();

    return size;
  }

  @Override
  public void recoverFromSnapshot(InputStream inputStream) throws Exception {
    data = reader.readValue(inputStream);
  }

  @Override
  public void reset() {
    data = ReflectUtil.newInstance(dataType);
  }

  @Override
  public long snapshotSize() {
    outBuffer.reset();

    try {
      writer.writeValue(outBuffer, data);
    } catch (Exception e) {
      throw new RuntimeException("Exception while writing json snapshot", e);
    }

    return outBuffer.size();
  }
}
