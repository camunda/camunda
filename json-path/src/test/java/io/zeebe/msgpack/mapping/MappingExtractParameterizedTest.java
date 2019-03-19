/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.msgpack.mapping;

import static io.zeebe.msgpack.mapping.MappingBuilder.createMapping;
import static io.zeebe.msgpack.mapping.MappingBuilder.createMappings;
import static io.zeebe.msgpack.mapping.MappingTestUtil.JSON_MAPPER;
import static io.zeebe.msgpack.mapping.MappingTestUtil.MSGPACK_MAPPER;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.msgpack.mapping.Mapping.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Represents a test class to test the extract document functionality with help of mappings. */
@RunWith(Parameterized.class)
public class MappingExtractParameterizedTest {

  @Parameters(name = "Test {index}: {0} to {2}")
  public static Iterable<Object[]> parameters() throws Exception {
    return Arrays.asList(
        new Object[][] {
          {
            // source
            "{'foo':'bar','int':1,'obj':{'test':'ok'},'array':[1,2,3]}",
            // mapping
            null,
            // expected result
            "{'foo':'bar','int':1,'obj':{'test':'ok'},'array':[1,2,3]}"
          },
          {
            // source
            "{'foo':'bar','int':1}",
            // mapping
            createMapping("foo", "newFoo"),
            // expected result
            "{'newFoo':'bar'}"
          },
          {
            // source
            "{'foo':'bar','int':1}",
            // mapping
            createMapping("foo", "newFoo.newDepth.string"),
            // expected result
            "{'newFoo':{'newDepth':{'string':'bar'}}}"
          },
          {
            // source
            "{'obj':{'attr':'text'},'int':1}",
            // mapping
            createMapping("obj", "newObj"),
            // expected result
            "{'newObj':{'attr':'text'}}"
          },
          {
            // source
            "{'array':[1, 2, 3],'int':1}",
            // mapping
            createMapping("array", "newArray"),
            // expected result
            "{'newArray':[1, 2, 3]}"
          },
          {
            // source
            "{'obj':{'test':'value'},'foo':'bar','array':[{'test':'value'}, 2, 3]}",
            // mapping
            createMappings().mapping("foo", "newFoo").mapping("obj", "newObj").build(),
            // expected result
            "{'newFoo':'bar', 'newObj':{'test':'value'}}"
          },
          {
            // source
            "{'obj':{'test':'value'},'foo':'bar','array':[{'test':'value'}, 2, 3]}",
            // mapping
            createMappings()
                .mapping("foo", "newDepth.newFoo")
                .mapping("obj", "newDepth.newObj")
                .build(),
            // expected result
            "{'newDepth':{'newFoo':'bar', 'newObj':{'test':'value'}}}"
          },
          {
            // source
            "{'obj':{'test':'value'},'foo':'bar','array':[{'test':'value'}, 2, 3]}",
            // mapping
            createMappings().mapping("foo", "newObj").mapping("obj", "newObj").build(),
            // expected result
            "{'newObj':{'test':'value'}}"
          },
          // zeebe-io/zeebe#297
          //            {
          //                // source
          //                "{'foo':'bar','int':1,'obj':{'test':'ok'},'array':[1,2,3]}",
          //                // mapping
          //                createMappings().mapping("$", "$.foo")
          //                                .mapping("$.obj", "$.foo.int").build(),
          //                // expected result
          //
          // "{'foo':{'foo':'bar','int':{'test':'ok'},'obj':{'test':'ok'},'array':[1,2,3]}}"
          //
          //            },
          {
            // source
            "{'a':{'bb':{'value':'x'}}, 'ab':{'b':{'value':'y'}}}}",
            // mapping
            createMapping("ab.b", "value"),
            // expected result
            "{'value':{'value':'y'}}"
          },
          {
            // source
            new String(
                Files.readAllBytes(
                    Paths.get(
                        MappingExtractParameterizedTest.class
                            .getResource("largeJsonDocument.json")
                            .toURI()))),
            // mapping
            createMapping("fourth.favoriteFruit", "favoriteFruit"),
            // expected result
            "{'favoriteFruit':'banana'}"
          },
          {
            // source
            "{'foo':{'bar':1}, 'foo.bar':2}",
            // mapping
            createMapping("foo.bar", "result"),
            // expected result
            "{'result':1}"
          },
          {
            // source
            "{'key':'val'}",
            // mapping
            createMapping("notAKey", "key"),
            // expected result
            "{'key':null}"
          },
          {
            // source
            "{'key':'val'}",
            // mapping
            createMapping("notAKey", "arr", Type.COLLECT),
            // expected result
            "{'arr':[null]}"
          },
        });
  }

  @Parameter public String sourceVariables;

  @Parameter(1)
  public Mapping[] mappings;

  @Parameter(2)
  public String expectedVariables;

  private MsgPackMergeTool mergeTool = new MsgPackMergeTool(1024);

  @Test
  public void shouldExtract() throws Throwable {
    // given variable
    final byte[] bytes = MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree(sourceVariables));
    final DirectBuffer sourceDocument = new UnsafeBuffer(bytes);

    // when
    mergeTool.reset();
    mergeTool.mergeDocument(sourceDocument, mappings);

    final DirectBuffer resultBuffer = mergeTool.writeResultToBuffer();
    final byte result[] = new byte[resultBuffer.capacity()];
    resultBuffer.getBytes(0, result, 0, result.length);

    // then
    assertThat(MSGPACK_MAPPER.readTree(result)).isEqualTo(JSON_MAPPER.readTree(expectedVariables));
  }
}
