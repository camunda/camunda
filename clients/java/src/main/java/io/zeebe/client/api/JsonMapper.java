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
package io.zeebe.client.api;

import java.io.InputStream;
import java.util.Map;

/**
 * This interface is using to customize the way how objects will be serialized and deserialized in
 * JSON format. The default implementation is {@link io.zeebe.client.impl.ZeebeObjectMapper}. This
 * interface could be implemented to customize the way how variables in the commands
 * serialized/deserialized. For example: there is such map with variables:
 *
 * <pre>
 *   final Map<String, Object> variables = new HashMap<>();
 *   variables.put("a", "b");
 *   variables.put("c", null);
 * </pre>
 *
 * And after doing this:
 *
 * <pre>
 *   public class MyJsonMapper implements JsonMapper {
 *
 *     private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().setSerializationInclusion(Include.NON_NULL);
 *
 *     public String toJson(final Object value) {
 *       return OBJECT_MAPPER.writeValueAsString(value);
 *     }
 *     ...
 *   }
 *   ...
 *   ZeebeClient.newClientBuilder().withJsonMapper(new MyJsonMapper());
 * </pre>
 *
 * Null values won't pass in the JSON with variables: {@code { "a": "b" } }
 *
 * @see io.zeebe.client.impl.ZeebeObjectMapper
 */
public interface JsonMapper {

  /**
   * Deserializes a JSON string into an equivalent POJO of type {@code T}.
   *
   * @param json the JSON string to deserialize
   * @param typeClass the Java type to deserialize into
   * @param <T> the type of the returned object
   * @return the POJO deserialized from the given JSON string
   * @throws io.zeebe.client.api.command.InternalClientException on serialization/deserialization
   *     error
   */
  <T> T fromJson(final String json, final Class<T> typeClass);

  /**
   * Deserializes a JSON string into a string to object map.
   *
   * @param json the JSON string to deserialize
   * @return the map deserialized from the given JSON string
   * @throws io.zeebe.client.api.command.InternalClientException on serialization/deserialization
   *     error
   */
  Map<String, Object> fromJsonAsMap(final String json);

  /**
   * Deserializes a JSON string into a string to string map.
   *
   * @param json the JSON string to deserialize
   * @return the map deserialized from the given JSON string
   * @throws io.zeebe.client.api.command.InternalClientException on serialization/deserialization
   *     error
   */
  Map<String, String> fromJsonAsStringMap(final String json);

  /**
   * Serializes an object (POJO, map, list, etc.) into an JSON string.
   *
   * @param value the object to serialize
   * @return a JSON string serialized from the given object
   * @throws io.zeebe.client.api.command.InternalClientException on serialization/deserialization
   *     error
   */
  String toJson(final Object value);

  /**
   * Validates a JSON string. If it is not valid throws a {@link
   * io.zeebe.client.api.command.InternalClientException}.
   *
   * @param propertyName the property name that contains the JSON string
   * @param jsonInput the JSON string
   * @return the same JSON string, that passed in
   * @throws io.zeebe.client.api.command.InternalClientException on serialization/deserialization
   *     error
   */
  String validateJson(final String propertyName, final String jsonInput);

  /**
   * Validates a stream that contains a JSON string. If it is not valid throws a {@link
   * io.zeebe.client.api.command.InternalClientException}
   *
   * @param propertyName a property name that contains the stream
   * @param jsonInput the stream that contains the JSON string
   * @return the JSON string from the stream
   * @throws io.zeebe.client.api.command.InternalClientException on serialization/deserialization
   *     error
   */
  String validateJson(final String propertyName, final InputStream jsonInput);
}
