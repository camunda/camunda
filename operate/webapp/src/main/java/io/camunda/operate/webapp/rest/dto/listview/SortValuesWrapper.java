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
package io.camunda.operate.webapp.rest.dto.listview;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.exceptions.OperateRuntimeException;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.elasticsearch.common.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SortValuesWrapper implements Serializable {

  private static final Logger LOGGER = LoggerFactory.getLogger(SortValuesWrapper.class);

  private static final Set<Class<?>> ALLOWED_SORTVALUE_TYPES = new HashSet<>();

  // These values were taken from org.elasticsearch.search.searchafter.SearchAfterBuilder.
  // Opensearch does not have a filter and just passes any type further on
  static {
    ALLOWED_SORTVALUE_TYPES.add(String.class);
    ALLOWED_SORTVALUE_TYPES.add(Text.class);
    ALLOWED_SORTVALUE_TYPES.add(Long.class);
    ALLOWED_SORTVALUE_TYPES.add(Integer.class);
    ALLOWED_SORTVALUE_TYPES.add(Short.class);
    ALLOWED_SORTVALUE_TYPES.add(Byte.class);
    ALLOWED_SORTVALUE_TYPES.add(Double.class);
    ALLOWED_SORTVALUE_TYPES.add(Float.class);
    ALLOWED_SORTVALUE_TYPES.add(Boolean.class);
    ALLOWED_SORTVALUE_TYPES.add(BigInteger.class);
  }

  public String value;
  public Class valueType;

  public SortValuesWrapper() {}

  public SortValuesWrapper(final String value, final Class valueType) {
    this.value = value;
    this.valueType = valueType;
  }

  public static SortValuesWrapper[] createFrom(
      final Object[] sortValues, final ObjectMapper objectMapper) {
    if (sortValues == null) {
      return null;
    }
    try {
      final List<SortValuesWrapper> sortValuesWrappers = new LinkedList<>();

      for (final Object sv : sortValues) {
        // Log if we are serializing a value that can't be deserialized later, will allow us to
        // discover new types to put in the allowlist
        if (!ALLOWED_SORTVALUE_TYPES.contains(sv.getClass())) {
          LOGGER.warn(
              "Serializing a sort value type that is not in the deserialization allowed list: {}",
              sv.getClass());
        }
        sortValuesWrappers.add(
            new SortValuesWrapper(objectMapper.writeValueAsString(sv), sv.getClass()));
      }
      return sortValuesWrappers.toArray(new SortValuesWrapper[0]);

    } catch (final JsonProcessingException e) {
      LOGGER.warn("Unable to serialize sortValues. Error: " + e.getMessage(), e);
      throw new OperateRuntimeException(e);
    }
  }

  public static Object[] convertSortValues(
      final SortValuesWrapper[] sortValuesWrappers, final ObjectMapper objectMapper) {
    if (sortValuesWrappers == null) {
      return null;
    }
    final List<Object> sortValues = new LinkedList<>();

    for (final SortValuesWrapper svw : sortValuesWrappers) {
      final var classType = svw.valueType;
      // These values can come as input from potentially untrusted sources. Sort values
      // are only expected to be of certain types, ensure that we don't try to deserialize
      // a bad type (both to prevent deserialization exploits and to not pass unsupported
      // types to elasticsearch/opensearch)
      if (ALLOWED_SORTVALUE_TYPES.contains(classType)) {
        try {
          sortValues.add(objectMapper.readValue(svw.value.getBytes(), classType));
        } catch (final IOException e) {
          LOGGER.error("Unable to deserialize sortValues. Error: {}", e.getMessage());
          throw new OperateRuntimeException(e);
        }
      } else {
        LOGGER.error("Unable to deserialize sortValues. Type {} is not allowed ", classType);
        throw new OperateRuntimeException("Invalid sortValues type: " + classType);
      }
    }

    return sortValues.toArray();
  }

  public Object getValue() {
    return value;
  }

  public SortValuesWrapper setValue(final String value) {
    this.value = value;
    return this;
  }

  public Class getValueType() {
    return valueType;
  }

  public SortValuesWrapper setValueType(final Class valueType) {
    this.valueType = valueType;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(value, valueType);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SortValuesWrapper that = (SortValuesWrapper) o;
    return Objects.equals(value, that.value) && Objects.equals(valueType, that.valueType);
  }
}
