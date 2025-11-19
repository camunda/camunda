/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.db.impl.rocksdb.transaction.ZeebeTransactionDb;
import io.camunda.zeebe.msgpack.MsgPackUtil;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.stream.impl.state.DbKeyGenerator;
import io.camunda.zeebe.stream.impl.state.NextValue;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class ThisisATest {

  @ParameterizedTest
  @ValueSource(
      longs = {
        1,
        3,
        5,
        // -3, doesnt work
        15,
        Short.MAX_VALUE,
        Integer.MAX_VALUE,
        (long) Integer.MAX_VALUE + Short.MAX_VALUE,
        (long) Integer.MAX_VALUE + Short.MAX_VALUE * 2,
        (long) Integer.MAX_VALUE + Integer.MAX_VALUE,
        (long) Integer.MAX_VALUE * 64,
        (long) Integer.MAX_VALUE << 5,
        (long) Integer.MAX_VALUE << 10,
        (long) Integer.MAX_VALUE << 20 // as soon we shift more, we exceed the max key size
      })
  public void shouldEncodeCorrectly(final long value) {
    // given
    final long key = Protocol.encodePartitionId(1, value);

    // when
    final MsgPackWriter msgPackWriter = new MsgPackWriter();
    final UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
    msgPackWriter.wrap(buffer, 0);
    final NextValue nextValue = new NextValue();
    nextValue.set(key);
    //    final int encodedLength = nextValue.getEncodedLength();
    nextValue.write(msgPackWriter);

    // then
    final var keyValuePair = MsgPackUtil.asMap(buffer.byteArray());
    System.out.println(keyValuePair);
    assertThat(keyValuePair).isEqualTo(Map.of("nextValue", key));
    assertThat(Protocol.decodeKeyInPartition(key)).isEqualTo(value);
    assertThat(Protocol.decodePartitionId(key)).isOne();
  }

  @ParameterizedTest
  @ValueSource(
      longs = {
        1,
        3,
        5,
        // -3, doesnt work
        15,
        Short.MAX_VALUE,
        Integer.MAX_VALUE,
        (long) Integer.MAX_VALUE + Short.MAX_VALUE,
        (long) Integer.MAX_VALUE + Short.MAX_VALUE * 2,
        (long) Integer.MAX_VALUE + Integer.MAX_VALUE,
        (long) Integer.MAX_VALUE * 64,
        (long) Integer.MAX_VALUE << 5,
        (long) Integer.MAX_VALUE << 10,
        (long) Integer.MAX_VALUE << 20 // as soon we shift more, we exceed the max key size
      })
  public void shouldStoreProperly(final long value, @TempDir final Path tempDir) {
    // given
    final long key = Protocol.encodePartitionId(1, value);
    final ZeebeTransactionDb<ZbColumnFamilies> db =
        new ZeebeRocksDbFactory<ZbColumnFamilies>(
                new RocksDbConfiguration(),
                new ConsistencyChecksSettings(),
                new AccessMetricsConfiguration(Kind.NONE, 1),
                SimpleMeterRegistry::new)
            .createDb(tempDir.toFile());
    final DbKeyGenerator dbKeyGenerator = new DbKeyGenerator(1, db, db.createContext());

    // when
    dbKeyGenerator.setKeyIfHigher(key);

    // then
    final long currentKey = dbKeyGenerator.getCurrentKey();
    assertThat(currentKey).isEqualTo(key);
    assertThat(Protocol.decodeKeyInPartition(currentKey)).isEqualTo(value);
    assertThat(Protocol.decodePartitionId(currentKey)).isOne();
  }
}
