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
            "{}",
            // mapping
            createMapping("$", "$"),
            // expected result
            "{}"
          },
          {
            // source
            "{'foo':'bar','int':1,'obj':{'test':'ok'},'array':[1,2,3]}",
            // mapping
            createMapping("$", "$"),
            // expected result
            "{'foo':'bar','int':1,'obj':{'test':'ok'},'array':[1,2,3]}"
          },
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
            createMapping("$.foo", "$.newFoo"),
            // expected result
            "{'newFoo':'bar'}"
          },
          {
            // source
            "{'foo':'bar','int':1}",
            // mapping
            createMapping("$[foo]", "$[newFoo]"),
            // expected result
            "{'newFoo':'bar'}"
          },
          {
            // source
            "{'foo':'bar','int':1}",
            // mapping
            createMapping("$.foo", "$.newFoo.newDepth.string"),
            // expected result
            "{'newFoo':{'newDepth':{'string':'bar'}}}"
          },
          {
            // source
            "{'foo':'bar','int':1}",
            // mapping
            createMapping("$[foo]", "$[newFoo][newDepth][string]"),
            // expected result
            "{'newFoo':{'newDepth':{'string':'bar'}}}"
          },
          {
            // source
            "{'obj':{'attr':'text'},'int':1}",
            // mapping
            createMapping("$.obj", "$.newObj"),
            // expected result
            "{'newObj':{'attr':'text'}}"
          },
          {
            // source
            "{'array':[1, 2, 3],'int':1}",
            // mapping
            createMapping("$.array", "$.newArray"),
            // expected result
            "{'newArray':[1, 2, 3]}"
          },
          {
            // source
            "{'array':[1, 2, 3],'int':1}",
            // mapping
            createMapping("$.array[0]", "$.firstIdxValue"),
            // expected result
            "{'firstIdxValue':1}"
          },
          {
            // source
            "{'array':[1, 2, 3],'int':1}",
            // mapping
            createMapping("$.array[1]", "$.array[0]"),
            // expected result
            "{'array':[2]}"
          },
          {
            // source
            "{'array':[1, 2, 3],'int':1}",
            // mapping
            createMapping("$[array][1]", "$[array][0]"),
            // expected result
            "{'array':[2]}"
          },
          {
            // source
            "{'array':[1, 2, 3],'int':1}",
            // mapping
            createMappings()
                .mapping("$.array[2]", "$.array[0]")
                .mapping("$.array[1]", "$.array[1]")
                .mapping("$.array[0]", "$.array[2]")
                .build(),
            // expected result
            "{'array':[3, 2, 1]}"
          },
          {
            // source
            "{'array':[1, 2, 3],'int':1}",
            // mapping
            createMapping("$.array[1]", "$.array[0].test"),
            // expected result
            "{'array':[{'test':2}]}"
          },
          {
            // source
            "{'array':[{'test':'value'}, 2, 3],'int':1}",
            // mapping
            createMapping("$.array[0].test", "$.testValue"),
            // expected result
            "{'testValue':'value'}"
          },
          {
            // source
            "{'obj':{'test':'value'},'foo':'bar','array':[{'test':'value'}, 2, 3]}",
            // mapping
            createMappings().mapping("$.foo", "$.newFoo").mapping("$.obj", "$.newObj").build(),
            // expected result
            "{'newFoo':'bar', 'newObj':{'test':'value'}}"
          },
          {
            // source
            "{'obj':{'test':'value'},'foo':'bar','array':[{'test':'value'}, 2, 3]}",
            // mapping
            createMappings()
                .mapping("$.foo", "$.newDepth.newFoo")
                .mapping("$.obj", "$.newDepth.newObj")
                .build(),
            // expected result
            "{'newDepth':{'newFoo':'bar', 'newObj':{'test':'value'}}}"
          },
          {
            // source
            "{'obj':{'test':'value'},'foo':'bar','array':[{'test':'value'}, 2, 3]}",
            // mapping
            createMappings().mapping("$.foo", "$.newObj").mapping("$.obj", "$.newObj").build(),
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
            "{'array':[[1,2],3,4], 'int':1}",
            // mapping
            createMapping("$.array[0]", "$.newArray"),
            // expected result
            "{'newArray':[1,2]}"
          },
          {
            // source
            "{'a':{'bb':{'value':'x'}}, 'ab':{'b':{'value':'y'}}}}",
            // mapping
            createMapping("$.ab.b", "$.value"),
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
            createMapping("$.fourth.friends[2].name", "$.name"),
            // expected result
            "{'name':'Preston Travis'}"
          },
          {
            // source
            "{'arr':[{'obj':{'value':'x', 'otherArr':[{'test':'hallo'}, {'obj':{'arr':[0, 1]}}]}}, {'otherValue':1}], 'ab':{'b':{'value':'y'}}}",
            // mapping
            createMapping("$.arr[0].obj.otherArr[1].obj.arr", "$.objArr"),
            // expected result
            "{'objArr':[0, 1]}}"
          },
          {
            // source
            "{'objArr':[0, 1]}}",
            // mapping
            createMapping("$.objArr", "$.arr[0].obj.otherArr[0].obj.arr"),
            // expected result
            "{'arr':[{'obj':{'otherArr':[{'obj':{'arr':[0, 1]}}]}}]}"
          },
          {
            // source
            "{'objArr':[0, 1]}}",
            // mapping
            createMapping("$.objArr", "$[arr][0][obj][otherArr][0][obj][arr]"),
            // expected result
            "{'arr':[{'obj':{'otherArr':[{'obj':{'arr':[0, 1]}}]}}]}"
          },
          {
            // source
            "{'foo':{'bar':1}, 'foo.bar':2}",
            // mapping
            createMapping("$['foo.bar']", "$.result"),
            // expected result
            "{'result':2}"
          },
          {
            // source
            "{'foo':{'bar':1}, 'foo.bar':2}",
            // mapping
            createMapping("$.foo.bar", "$.result"),
            // expected result
            "{'result':1}"
          },
          {
            // source
            "{'foo':{'bar':1}, 'foo.bar':2}",
            // mapping
            createMapping("$[foo][bar]", "$.result"),
            // expected result
            "{'result':1}"
          },
          {
            // source
            "{'foo':{'bar':1}, 'foo.bar':2}",
            // mapping
            createMapping("$['foo']['bar']", "$.result"),
            // expected result
            "{'result':1}"
          },
          {
            // source
            "{'key':'val'}",
            // mapping
            createMapping("$.notAKey", "$.key"),
            // expected result
            "{'key':null}"
          },
          {
            // source
            "{'key':'val'}",
            // mapping
            createMapping("$.notAKey", "$.arr", Type.COLLECT),
            // expected result
            "{'arr':[null]}"
          },
          {
            // source
            "{'key1':'val1', 'key2': 'val2'}",
            // mapping
            createMapping("$.*", "$.newKey", Type.PUT),
            // expected result
            "{'newKey': 'val1'}" // selecting the first element
          },
          {
            // source
            "{'key1':'val1', 'key2': 'val2'}",
            // mapping
            createMapping("$.*", "$.newKey", Type.COLLECT),
            // expected result
            "{'newKey': ['val1']}" // selecting the first element - collecting all elements would be
            // nicer
          },
          {
            // source
            "{'key':'val'}",
            // mapping
            createMapping("$.key", "$", Type.PUT),
            // expected result
            "{}" // empty object - the best we can reasonably do in this rare case
          }
        });
  }

  @Parameter public String sourcePayload;

  @Parameter(1)
  public Mapping[] mappings;

  @Parameter(2)
  public String expectedPayload;

  private MsgPackMergeTool mergeTool = new MsgPackMergeTool(1024);

  @Test
  public void shouldExtract() throws Throwable {
    // given payload
    final byte[] bytes = MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree(sourcePayload));
    final DirectBuffer sourceDocument = new UnsafeBuffer(bytes);

    // when
    mergeTool.reset();
    mergeTool.mergeDocument(sourceDocument, mappings);

    final DirectBuffer resultBuffer = mergeTool.writeResultToBuffer();
    final byte result[] = new byte[resultBuffer.capacity()];
    resultBuffer.getBytes(0, result, 0, result.length);

    // then
    assertThat(MSGPACK_MAPPER.readTree(result)).isEqualTo(JSON_MAPPER.readTree(expectedPayload));
  }
}
