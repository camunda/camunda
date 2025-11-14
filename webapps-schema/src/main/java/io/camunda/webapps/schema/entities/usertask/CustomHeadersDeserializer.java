package io.camunda.webapps.schema.entities.usertask;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom JSON deserializer that converts nested array format from Elasticsearch/OpenSearch back to
 * Map&lt;String, String&gt;.
 *
 * <p>Transforms:
 * <pre>
 * [{"name": "department", "value": "engineering"}, {"name": "priority", "value": "high"}]
 * </pre>
 * to:
 * <pre>
 * {"department": "engineering", "priority": "high"}
 * </pre>
 */
public final class CustomHeadersDeserializer extends JsonDeserializer<Map<String, String>> {

  @Override
  public Map<String, String> deserialize(final JsonParser p, final DeserializationContext ctxt)
      throws IOException {
    final JsonNode node = p.getCodec().readTree(p);

    if (node == null || node.isNull()) {
      return null;
    }

    final Map<String, String> headers = new HashMap<>();

    if (node.isArray()) {
      // Nested array format from ES/OS: [{"name": "key", "value": "val"}]
      for (final JsonNode item : node) {
        final String name = item.get("name").asText();
        final String value = item.get("value").asText();
        headers.put(name, value);
      }
    } else if (node.isObject()) {
      // Legacy object format support (for backwards compatibility)
      node.fields()
          .forEachRemaining(entry -> headers.put(entry.getKey(), entry.getValue().asText()));
    }

    return headers;
  }
}
