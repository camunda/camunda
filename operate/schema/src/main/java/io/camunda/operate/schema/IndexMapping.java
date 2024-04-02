/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.exceptions.OperateRuntimeException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class IndexMapping {

  private String indexName;

  private String dynamic;

  private Set<IndexMappingProperty> properties;

  public String getIndexName() {
    return indexName;
  }

  public IndexMapping setIndexName(final String indexName) {
    this.indexName = indexName;
    return this;
  }

  public String getDynamic() {
    // Opensearch changes the capitalization of this field on some query results, change to
    // lowercase for consistency
    return dynamic == null ? null : dynamic.toLowerCase();
  }

  public IndexMapping setDynamic(final String dynamic) {
    // Opensearch changes the capitalization of this field on some query results, change to
    // lowercase for consistency
    this.dynamic = dynamic == null ? null : dynamic.toLowerCase();
    return this;
  }

  public Set<IndexMappingProperty> getProperties() {
    return properties;
  }

  public IndexMapping setProperties(final Set<IndexMappingProperty> properties) {
    this.properties = properties;
    return this;
  }

  public Map<String, Object> toMap() {
    return properties.stream()
        .collect(
            Collectors.toMap(
                IndexMappingProperty::getName, IndexMappingProperty::getTypeDefinition));
  }

  @Override
  public int hashCode() {
    return Objects.hash(indexName, dynamic, properties);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final IndexMapping that = (IndexMapping) o;
    return Objects.equals(indexName, that.indexName)
        && Objects.equals(dynamic, that.dynamic)
        && Objects.equals(properties, that.properties);
  }

  @Override
  public String toString() {
    return "IndexMapping{"
        + "indexName='"
        + indexName
        + '\''
        + ", dynamic='"
        + dynamic
        + '\''
        + ", properties="
        + properties
        + '}';
  }

  public static class IndexMappingProperty {

    private String name;

    private Object typeDefinition;

    public String getName() {
      return name;
    }

    public IndexMappingProperty setName(final String name) {
      this.name = name;
      return this;
    }

    public Object getTypeDefinition() {
      return typeDefinition;
    }

    public IndexMappingProperty setTypeDefinition(final Object typeDefinition) {
      this.typeDefinition = typeDefinition;
      return this;
    }

    public static String toJsonString(
        final Set<IndexMappingProperty> properties, final ObjectMapper objectMapper) {
      try {
        final Map<String, Object> fields =
            properties.stream()
                .collect(Collectors.toMap(p -> p.getName(), p -> p.getTypeDefinition()));
        return objectMapper.writeValueAsString(fields);
      } catch (final JsonProcessingException e) {
        throw new OperateRuntimeException(e);
      }
    }

    public static IndexMappingProperty createIndexMappingProperty(
        final Entry<String, Object> propertiesMapEntry) {
      return new IndexMappingProperty()
          .setName(propertiesMapEntry.getKey())
          .setTypeDefinition(propertiesMapEntry.getValue());
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, typeDefinition);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final IndexMappingProperty that = (IndexMappingProperty) o;
      return Objects.equals(name, that.name) && Objects.equals(typeDefinition, that.typeDefinition);
    }

    @Override
    public String toString() {
      return "IndexMappingProperty{"
          + "name='"
          + name
          + '\''
          + ", typeDefinition="
          + typeDefinition
          + '}';
    }
  }
}
