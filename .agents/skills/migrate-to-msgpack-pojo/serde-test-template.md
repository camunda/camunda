# Template for `<Name>SerdeTest`

Copy into `<module>/src/test/java/<package path>/<Name>SerdeTest.java` and replace `Name`. Keep
the structure and the `// given // when // then` comments. `NameV1` is the frozen copy of the old
class, next to this test in `src/test`. Kind A example: `zeebe/engine/src/test/java/io/camunda/zeebe/engine/state/instance/ElementInstanceSerdeTest.java`.

Adjust the tests to the class:

- Derive expected defaults from a fresh instance (`new NameV1()`), never from memory.
- Nested records: set every field the class stores to a non-default value in test 1. Include one
  value large enough to change the msgpack header (a 40 byte string, a list of 20 entries).
- Properties without a default cannot be written by the old class while unset: set them on both
  objects, also in the "defaults" test.
- Number lists (`IntArrayList`, `LongArrayList`): include a value above 127 and one above 65535 so
  the msgpack integer width changes.
- Classes that never copied on read (views on the input buffer, like the old properties) get NO
  aliasing test. Keep `shouldNotReferenceTheBufferItWasReadFrom` only if the old class cloned or
  copied the input in `wrap`.
- `shouldProduceTheSameJsonAsV1` is for kind B (records) only; delete it for state classes.

```java
package <package>;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link NameV1} is a frozen copy of the property based implementation the generated codec
 * replaced.
 */
final class NameSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new NameV1();
    final var current = new Name();
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
    // given only what cannot be written unset, everything else stays default
    final var v1 = new NameV1();
    final var current = new Name();
    // TODO: set the properties that have no default, on both

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
    final var v1 = new NameV1();
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
    final var current = new Name();
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
    // given a value that carries a property this version does not know
    final var source = new Name();
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
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

  // keep only if the old class cloned on read or copied the input in wrap
  @Test
  void shouldNotReferenceTheBufferItWasReadFrom() {
    // given
    final var instance = new Name();
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

  // kind B (records) only
  @Test
  void shouldProduceTheSameJsonAsV1() {
    // given
    final var v1 = new NameV1();
    final var current = new Name();
    // TODO: set EVERY property to a non-default value on BOTH objects (same values as the first
    // test; extract a helper if it is long)

    // when
    final var v1Json = v1.toJson();
    final var currentJson = current.toJson();

    // then exporters see exactly the same document
    assertThat(currentJson).isEqualTo(v1Json);
  }

  private static DirectBuffer utf8(final String s) {
    return new UnsafeBuffer(s.getBytes(StandardCharsets.UTF_8));
  }

  private static String hex(final ExpandableArrayBuffer buffer, final int length) {
    final var bytes = new byte[length];
    buffer.getBytes(0, bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
```
