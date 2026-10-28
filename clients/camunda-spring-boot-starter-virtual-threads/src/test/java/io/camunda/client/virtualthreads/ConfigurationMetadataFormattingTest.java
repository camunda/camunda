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
package io.camunda.client.virtualthreads;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.util.ResourceUtils;

/**
 * Enforces the documentation conventions of the main starter's metadata tests on this module's
 * {@code META-INF/additional-spring-configuration-metadata.json}: every group and property carries
 * a description that ends with a period and uses no markdown formatting.
 */
class ConfigurationMetadataFormattingTest {

  @TestFactory
  Stream<DynamicContainer> formattingTest() throws IOException {
    final ObjectMapper objectMapper = new ObjectMapper();
    final JsonNode jsonNode =
        objectMapper.readTree(
            ResourceUtils.getFile(
                "classpath:META-INF/additional-spring-configuration-metadata.json"));

    return Stream.of(createGroupsTests(jsonNode), createPropertiesTests(jsonNode));
  }

  private DynamicContainer createPropertiesTests(final JsonNode metaData) {
    final ArrayNode properties = (ArrayNode) metaData.get("properties");

    return DynamicContainer.dynamicContainer(
        "Properties",
        properties
            .valueStream()
            .filter(property -> !property.has("deprecation"))
            .map(
                property ->
                    DynamicTest.dynamicTest(
                        property.get("name").asText(),
                        () -> {
                          assertThat(property.has("description")).isTrue();
                          final String description = property.get("description").asText();
                          assertThat(description).endsWith(".");
                          assertThat(description).doesNotContain("`", "**", "_");
                        })));
  }

  private DynamicContainer createGroupsTests(final JsonNode metaData) {
    final ArrayNode groups = (ArrayNode) metaData.get("groups");

    return DynamicContainer.dynamicContainer(
        "Groups",
        groups
            .valueStream()
            .filter(group -> group.get("name").asText().startsWith("camunda.client"))
            .map(
                group ->
                    DynamicTest.dynamicTest(
                        group.get("name").asText(),
                        () -> {
                          assertThat(group.has("description")).isTrue();
                          final String description = group.get("description").asText();
                          assertThat(description).endsWith(".");
                          assertThat(description).doesNotContain("`", "**", "_");
                        })));
  }
}
