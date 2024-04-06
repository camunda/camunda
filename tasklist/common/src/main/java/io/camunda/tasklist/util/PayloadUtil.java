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
package io.camunda.tasklist.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PayloadUtil {

  @Autowired private ObjectMapper objectMapper;

  public Map<String, Object> parsePayload(String payload) throws IOException {

    final Map<String, Object> map = new LinkedHashMap<>();

    traverseTheTree(objectMapper.readTree(payload), map, "");

    return map;
  }

  public String readJSONStringFromClasspath(String filename) {
    try (InputStream inputStream = PayloadUtil.class.getResourceAsStream(filename)) {
      if (inputStream != null) {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
      } else {
        throw new TasklistRuntimeException("Failed to find " + filename + " in classpath ");
      }
    } catch (IOException e) {
      throw new TasklistRuntimeException("Failed to load file " + filename + " from classpath ", e);
    }
  }

  private void traverseTheTree(JsonNode jsonNode, Map<String, Object> map, String path) {
    if (jsonNode.isValueNode()) {

      Object value = null;

      switch (jsonNode.getNodeType()) {
        case BOOLEAN:
          value = jsonNode.booleanValue();
          break;
        case NUMBER:
          switch (jsonNode.numberType()) {
            case INT:
            case LONG:
            case BIG_INTEGER:
              value = jsonNode.longValue();
              break;
            case FLOAT:
            case DOUBLE:
            case BIG_DECIMAL:
              value = jsonNode.doubleValue();
              break;
            default:
              break;
          }
          break;
        case STRING:
          value = jsonNode.textValue();
          break;
        case NULL:
          break;
        case BINARY:
          // TODO
          break;
        default:
          break;
      }
      map.put(path, value);

    } else if (jsonNode.isContainerNode()) {
      if (jsonNode.isObject()) {
        final Iterator<String> fieldIterator = jsonNode.fieldNames();
        while (fieldIterator.hasNext()) {
          final String fieldName = fieldIterator.next();
          traverseTheTree(
              jsonNode.get(fieldName), map, (path.isEmpty() ? "" : path + ".") + fieldName);
        }
      } else if (jsonNode.isArray()) {
        int i = 0;
        for (JsonNode child : jsonNode) {
          traverseTheTree(child, map, path + "[" + i + "]");
          i++;
        }
      }
    }
  }
}
