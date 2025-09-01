package io.camunda.zeebe.gateway.validation.runtime.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.camunda.zeebe.gateway.validation.OneOfGroup;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Jackson module for capturing first-level JSON token kinds for selected classes. Designed for
 * test and incremental integration; not yet auto-registered in Spring Boot.
 */
public final class TokenCaptureModule extends SimpleModule {

  private TokenCaptureModule(final Class<?>[] targets) {
    for (final Class<?> c : targets) {
      final OneOfGroup ann = c.getAnnotation(OneOfGroup.class);
      if (ann != null && (ann.captureRawTokens() || ann.strictTokenKinds())) {
        @SuppressWarnings({"rawtypes", "unchecked"})
        final JsonDeserializer deser = new CapturingDeserializer(c);
        addDeserializer((Class) c, deser);
      }
    }
  }

  public static TokenCaptureModule forClasses(final Class<?>... targets) {
    return new TokenCaptureModule(targets);
  }

  private static final class CapturingDeserializer extends JsonDeserializer<Object> {
    private final Class<?> type;

    private CapturingDeserializer(final Class<?> type) {
      this.type = type;
    }

    @Override
    public Object deserialize(final JsonParser p, final DeserializationContext ctxt) throws IOException {
      final ObjectCodec codec = p.getCodec();
      final JsonNode node = codec.readTree(p);
      final Map<String, String> kinds = new HashMap<>();
      // capture top-level & nested token kinds using JSON Pointer style keys (no leading slash for top-level)
      collectKinds(node, "", kinds, 0);
      final ObjectMapper mapper = (ObjectMapper) codec;
      final Object bean = mapper.convertValue(node, type);
      tryInjectTokens(bean, kinds);
      return bean;
    }

    private void collectKinds(final JsonNode node, final String path, final Map<String, String> kinds, final int depth) {
      if (node == null) {
        return;
      }
      if (node.isObject()) {
        final var fields = node.fields();
        while (fields.hasNext()) {
          final var e = fields.next();
          final String name = e.getKey();
          final JsonNode child = e.getValue();
          final String key = depth == 0 ? name : path + "/" + escape(name);
          kinds.put(depth == 0 ? name : key, classify(child)); // maintain existing top-level behaviour
          // Recurse for nested objects/arrays to allow future deep token validation
          if (child.isObject() || child.isArray()) {
            collectKinds(child, key, kinds, depth + 1);
          }
        }
      } else if (node.isArray()) {
        int idx = 0;
        for (final JsonNode child : node) {
          final String key = path + "/" + idx;
          kinds.put(key, classify(child));
          if (child.isObject() || child.isArray()) {
            collectKinds(child, key, kinds, depth + 1);
          }
          idx++;
        }
      }
    }

    private String escape(final String name) {
      return name.replace("~", "~0").replace("/", "~1");
    }

    private String classify(final JsonNode n) {
      if (n.isTextual()) return "STRING";
      if (n.isNumber()) return "NUMBER";
      if (n.isBoolean()) return "BOOLEAN";
      if (n.isArray()) return "ARRAY";
      if (n.isNull()) return "NULL";
      if (n.isObject()) return "OBJECT";
      return "UNKNOWN";
    }

    private void tryInjectTokens(final Object bean, final Map<String, String> kinds) {
      try {
        final var f = bean.getClass().getDeclaredField("tokens");
        f.setAccessible(true);
        f.set(bean, kinds);
      } catch (NoSuchFieldException | IllegalAccessException ignored) {
        // bean not prepared for token injection; ignore
      }
    }
  }
}
