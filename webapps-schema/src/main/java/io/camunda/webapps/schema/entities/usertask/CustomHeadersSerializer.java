package io.camunda.webapps.schema.entities.usertask;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.util.Map;

/**
 * Custom JSON serializer that converts Map&lt;String, String&gt; to nested array format for
 * Elasticsearch/OpenSearch.
 *
 * <p>Transforms:
 * <pre>
 * {"department": "engineering", "priority": "high"}
 * </pre>
 * to:
 * <pre>
 * [{"name": "department", "value": "engineering"}, {"name": "priority", "value": "high"}]
 * </pre>
 */
public final class CustomHeadersSerializer extends JsonSerializer<Map<String, String>> {

  @Override
  public void serialize(
      final Map<String, String> value,
      final JsonGenerator gen,
      final SerializerProvider serializers)
      throws IOException {
    if (value == null || value.isEmpty()) {
      gen.writeNull();
      return;
    }

    gen.writeStartArray();
    for (final Map.Entry<String, String> entry : value.entrySet()) {
      gen.writeStartObject();
      gen.writeStringField("name", entry.getKey());
      gen.writeStringField("value", entry.getValue());
      gen.writeEndObject();
    }
    gen.writeEndArray();
  }
}
