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
package io.camunda.client.impl.search.filter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.std.DelegatingDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import io.camunda.client.protocol.rest.StringFilterProperty;
import java.io.IOException;
import java.util.List;

/**
 * Preserves cross-version compatibility for {@code StringFilterProperty} search filters.
 *
 * <p>The REST schema models these filters as {@code oneOf: [string, AdvancedStringFilter]}: a bare
 * string is an exact match, while the object form carries advanced operators ({@code $eq}, {@code
 * $like}, {@code $in}, ...). Fields such as {@code role.roleId}, {@code mappingRule.mappingRuleId}
 * and {@code group.name} were a plain {@code string} before 8.10 and only gained the advanced
 * object form in 8.10. The Java client's generated model collapses the {@code oneOf} into the
 * object POJO alone, so an exact-match convenience call (e.g. {@code .roleId("admin")}) would
 * always serialize as {@code {"roleId":{"$eq":"admin"}}} — which a pre-8.10 cluster rejects,
 * breaking an 8.10 client against an 8.9 cluster with no caller code change.
 *
 * <p>This module restores the backward-compatible wire form: when a {@code StringFilterProperty}
 * carries only {@code $eq} (a pure exact match) it is written as a bare string, which every version
 * accepts and is semantically identical to {@code {"$eq": ...}}. Any advanced operator still
 * serializes as the 8.10 object form. A matching deserializer maps a bare string back to {@code
 * $eq} so round-tripping the request body remains lossless.
 */
public final class StringFilterPropertyModule extends SimpleModule {

  public StringFilterPropertyModule() {
    super("StringFilterPropertyModule");
    setSerializerModifier(
        new BeanSerializerModifier() {
          @Override
          public JsonSerializer<?> modifySerializer(
              final SerializationConfig config,
              final BeanDescription beanDesc,
              final JsonSerializer<?> serializer) {
            if (beanDesc.getBeanClass() == StringFilterProperty.class) {
              @SuppressWarnings("unchecked")
              final JsonSerializer<Object> delegate = (JsonSerializer<Object>) serializer;
              return new ExactMatchAwareSerializer(delegate);
            }
            return serializer;
          }
        });
    setDeserializerModifier(
        new BeanDeserializerModifier() {
          @Override
          public JsonDeserializer<?> modifyDeserializer(
              final DeserializationConfig config,
              final BeanDescription beanDesc,
              final JsonDeserializer<?> deserializer) {
            if (beanDesc.getBeanClass() == StringFilterProperty.class) {
              return new BareStringAwareDeserializer(deserializer);
            }
            return deserializer;
          }
        });
  }

  private static boolean isExactMatchOnly(final StringFilterProperty value) {
    return value.get$Eq() != null
        && value.get$Neq() == null
        && value.get$Exists() == null
        && isEmpty(value.get$In())
        && isEmpty(value.get$NotIn())
        && value.get$Like() == null;
  }

  private static boolean isEmpty(final List<String> list) {
    return list == null || list.isEmpty();
  }

  /**
   * Writes an exact-match-only {@link StringFilterProperty} as a bare string; delegates every other
   * shape to the default bean serializer so the advanced object form is preserved verbatim.
   */
  private static final class ExactMatchAwareSerializer
      extends JsonSerializer<StringFilterProperty> {

    private final JsonSerializer<Object> delegate;

    private ExactMatchAwareSerializer(final JsonSerializer<Object> delegate) {
      this.delegate = delegate;
    }

    @Override
    public void serialize(
        final StringFilterProperty value,
        final JsonGenerator gen,
        final SerializerProvider serializers)
        throws IOException {
      if (isExactMatchOnly(value)) {
        gen.writeString(value.get$Eq());
      } else {
        delegate.serialize(value, gen, serializers);
      }
    }
  }

  /**
   * Maps a bare JSON string back to an exact-match {@link StringFilterProperty} ({@code $eq});
   * delegates the object form to the default bean deserializer.
   */
  private static final class BareStringAwareDeserializer extends DelegatingDeserializer {

    private BareStringAwareDeserializer(final JsonDeserializer<?> delegate) {
      super(delegate);
    }

    @Override
    protected JsonDeserializer<?> newDelegatingInstance(final JsonDeserializer<?> newDelegatee) {
      return new BareStringAwareDeserializer(newDelegatee);
    }

    @Override
    public Object deserialize(final JsonParser p, final DeserializationContext ctxt)
        throws IOException {
      if (p.hasToken(JsonToken.VALUE_STRING)) {
        return new StringFilterProperty().$eq(p.getValueAsString());
      }
      return super.deserialize(p, ctxt);
    }
  }
}
