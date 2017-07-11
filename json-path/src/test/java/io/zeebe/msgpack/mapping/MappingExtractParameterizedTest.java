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

import static org.assertj.core.api.Assertions.assertThat;
import static io.zeebe.msgpack.mapping.MappingBuilder.createMapping;
import static io.zeebe.msgpack.mapping.MappingBuilder.createMappings;
import static io.zeebe.msgpack.mapping.MappingTestUtil.JSON_MAPPER;
import static io.zeebe.msgpack.mapping.MappingTestUtil.MSGPACK_MAPPER;
import static org.junit.runners.Parameterized.Parameter;
import static org.junit.runners.Parameterized.Parameters;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Represents a test class to test the extract document functionality with help of mappings.
 */
@RunWith(Parameterized.class)
public class MappingExtractParameterizedTest
{

    @Parameters(name = "Test {index}: {0} to {2}")
    public static Iterable<Object[]> parameters() throws Exception
    {
        return Arrays.asList(new Object[][] {
            // 1. Test
            {
                // source
                "{}",
                // mapping
                createMapping("$", "$"),
                // expected result
                "{}" },
            // 2. Test
            {
                // source
                "{'foo':'bar','int':1,'obj':{'test':'ok'},'array':[1,2,3]}",
                // mapping
                createMapping("$", "$"),
                // expected result
                "{'foo':'bar','int':1,'obj':{'test':'ok'},'array':[1,2,3]}" },
            // 3. Test
            {
                // source
                "{'foo':'bar','int':1}",
                // mapping
                createMapping("$.foo", "$.newFoo"),
                // expected result
                "{'newFoo':'bar'}" },
            // 4. Test
            {
                // source
                "{'foo':'bar','int':1}",
                // mapping
                createMapping("$.foo", "$.newFoo.newDepth.string"),
                // expected result
                "{'newFoo':{'newDepth':{'string':'bar'}}}" },
            // 5. Test
            {
                // source
                "{'obj':{'attr':'text'},'int':1}",
                // mapping
                createMapping("$.obj", "$.newObj"),
                // expected result
                "{'newObj':{'attr':'text'}}" },
            // 6. Test
            {
                // source
                "{'array':[1, 2, 3],'int':1}",
                // mapping
                createMapping("$.array", "$.newArray"),
                // expected result
                "{'newArray':[1, 2, 3]}" },
            // 7. Test
            {
                // source
                "{'array':[1, 2, 3],'int':1}",
                // mapping
                createMapping("$.array[0]", "$.firstIdxValue"),
                // expected result
                "{'firstIdxValue':1}" },
            // 8. Test
            {
                // source
                "{'array':[1, 2, 3],'int':1}",
                // mapping
                createMapping("$.array[1]", "$.array[0]"),
                // expected result
                "{'array':[2]}" },
            // 9. Test
            {
                // source
                "{'array':[1, 2, 3],'int':1}",
                // mapping
                createMappings().mapping("$.array[2]", "$.array[0]")
                                .mapping("$.array[1]", "$.array[1]")
                                .mapping("$.array[0]", "$.array[2]").build(),
                // expected result
                "{'array':[3, 2, 1]}" },
            // 10. Test
            {
                // source
                "{'array':[1, 2, 3],'int':1}",
                // mapping
                createMapping("$.array[1]", "$.array[0].test"),
                // expected result
                "{'array':[{'test':2}]}" },
            // 11. Test
            {
                // source
                "{'array':[{'test':'value'}, 2, 3],'int':1}",
                // mapping
                createMapping("$.array[0].test", "$.testValue"),
                // expected result
                "{'testValue':'value'}" },
            // 12. Test
            {
                // source
                "{'obj':{'test':'value'},'foo':'bar','array':[{'test':'value'}, 2, 3]}",
                // mapping
                createMappings().mapping("$.foo", "$.newFoo")
                                .mapping("$.obj", "$.newObj").build(),
                // expected result
                "{'newFoo':'bar', 'newObj':{'test':'value'}}" },
            // 13. Test
            {
                // source
                "{'obj':{'test':'value'},'foo':'bar','array':[{'test':'value'}, 2, 3]}",
                // mapping
                createMappings().mapping("$.foo", "$.newDepth.newFoo")
                                .mapping("$.obj", "$.newDepth.newObj").build(),
                // expected result
                "{'newDepth':{'newFoo':'bar', 'newObj':{'test':'value'}}}" },
            // 14. Test
            {
                // source
                "{'obj':{'test':'value'},'foo':'bar','array':[{'test':'value'}, 2, 3]}",
                // mapping
                createMappings().mapping("$.foo", "$.newObj")
                                .mapping("$.obj", "$.newObj").build(),
                // expected result
                "{'newObj':{'test':'value'}}" },
            // 15. Test camunda-tngp/camunda-tngp#297
//            {
//                // source
//                "{'foo':'bar','int':1,'obj':{'test':'ok'},'array':[1,2,3]}",
//                // mapping
//                createMappings().mapping("$", "$.foo")
//                                .mapping("$.obj", "$.foo.int").build(),
//                // expected result
//                "{'foo':{'foo':'bar','int':{'test':'ok'},'obj':{'test':'ok'},'array':[1,2,3]}}"
//
//            },
            // 16.Test
            {
                // source
                "{'array':[[1,2],3,4], 'int':1}",
                // mapping
                createMapping("$.array[0]", "$.newArray"),
                // expected result
                "{'newArray':[1,2]}" },
            // 17.Test
            {
                // source
                "{'a':{'bb':{'value':'x'}}, 'ab':{'b':{'value':'y'}}}}",
                // mapping
                createMapping("$.ab.b", "$.value"),
                // expected result
                "{'value':{'value':'y'}}" },
            // 18.Test
            {
                // source
                new String(Files.readAllBytes(Paths.get(MappingExtractParameterizedTest.class.getResource("largeJsonDocument.json")
                                                                                             .toURI()))),
                // mapping
                createMapping("$.fourth.friends[2].name", "$.name"),
                // expected result
                "{'name':'Preston Travis'}" },
            // 19.Test
            {
                // source
                "{'arr':[{'obj':{'value':'x', 'otherArr':[{'test':'hallo'}, {'obj':{'arr':[0, 1]}}]}}, {'otherValue':1}], 'ab':{'b':{'value':'y'}}}",
                // mapping
                createMapping("$.arr[0].obj.otherArr[1].obj.arr", "$.objArr"),
                // expected result
                "{'objArr':[0, 1]}}" },
            // 20.Test
            {
                // source
                "{'objArr':[0, 1]}}",
                // mapping
                createMapping("$.objArr", "$.arr[0].obj.otherArr[0].obj.arr"),
                // expected result
                "{'arr':[{'obj':{'otherArr':[{'obj':{'arr':[0, 1]}}]}}]}" }});
    }

    @Parameter
    public String sourcePayload;

    @Parameter(1)
    public Mapping[] mappings;

    @Parameter(2)
    public String expectedPayload;

    private MappingProcessor processor = new MappingProcessor(1024);

    @Test
    public void shouldExtract() throws Throwable
    {
        // given payload
        final byte[] bytes = MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree(sourcePayload));
        final DirectBuffer sourceDocument = new UnsafeBuffer(bytes);

        // when
        final int resultLength = processor.extract(sourceDocument, mappings);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then
        assertThat(MSGPACK_MAPPER.readTree(result)).isEqualTo(JSON_MAPPER.readTree(expectedPayload));
    }
}