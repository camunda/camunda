# Template for `<Name>SerdeTest`

Copy into `<module>/src/test/java/<package path>/<Name>SerdeTest.java`. Replace `Name`,
fill the `TODO`s. Keep the structure and the `// given // when // then` comments. The real example is
`zeebe/engine/src/test/java/io/camunda/zeebe/engine/state/instance/ElementInstanceSerdeTest.java`.

```java
package <package>;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.util.HexFormat;
import org.agrona.ExpandableArrayBuffer;
import org.junit.jupiter.api.Test;

/**
 * The stored format must not change, otherwise migration and rollback break. {@link NameV1} is a
 * frozen copy of the hand-written implementation the generated codec replaced.
 */
final class NameSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new NameV1(/* TODO required constructor args */);
    final var current = new Name(/* TODO same args */);
    // TODO: set EVERY property to a non-default value on BOTH objects, one pair of lines each:
    //   v1.setX(5L);
    //   current.setX(5L);

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(currentLength).isEqualTo(current.getLength()).isEqualTo(v1.getLength());
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldWriteTheSameBytesAsV1WithDefaults() {
    // given only what the constructor requires, everything else stays default
    final var v1 = new NameV1(/* TODO */);
    final var current = new Name(/* TODO */);

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new NameV1(/* TODO */);
    // TODO: set a few properties to non-default values
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new Name();
    // TODO: dirty EVERY property of the target (all setters, add a list element, ...), so that a
    // default that is not restored by reset() makes this test fail

    // when
    current.wrap(bytes, 0, length);

    // then
    // TODO: assert every value you set, and that the dirtied ones are back to their default
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new Name(/* TODO */);
    // TODO: set a few properties
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new NameV1();
    v1.wrap(bytes, 0, length);

    // then
    // TODO: assert the values through the V1 getters
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a stored value that carries a property this version does not know
    final var source = new Name(/* TODO */);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(wrapString("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // The map header says how many entries follow. Up to 15 entries it is one byte (0x80 | count),
    // so announce one more with `stored.putByte(0, (byte) (stored.getByte(0) + 1))`. With 16 or
    // more entries it is 0xde followed by a 2 byte count, then increment the byte at index 2:
    // TODO pick the right one for this class and delete the other.
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new Name();
    instance.wrap(stored, 0, storedLength + futureLength);
    // TODO: modify one property
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  // keep this test only if the class has string, list or nested UnpackedObject properties
  @Test
  void shouldNotReferenceTheBufferItWasReadFrom() {
    // given
    final var instance = new Name(/* TODO */);
    // TODO: set the string / list properties
    final var buffer = new ExpandableArrayBuffer();
    final int length = instance.write(buffer, 0);
    final var read = new Name();
    read.wrap(buffer, 0, length);

    // when the buffer is reused
    buffer.setMemory(0, buffer.capacity(), (byte) 0);

    // then
    // TODO: assert the string / list values are unchanged
  }

  private static org.agrona.DirectBuffer wrapString(final String s) {
    return new org.agrona.concurrent.UnsafeBuffer(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private static String hex(final ExpandableArrayBuffer buffer, final int length) {
    final var bytes = new byte[length];
    buffer.getBytes(0, bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
```
