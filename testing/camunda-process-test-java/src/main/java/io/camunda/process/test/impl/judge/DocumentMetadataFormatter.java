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
package io.camunda.process.test.impl.judge;

import io.camunda.process.test.api.judge.ResolvedDocument;

/**
 * Renders the metadata attribute string used to identify a resolved document, both inside the
 * {@code <resolved_documents>} prompt section and inside per-block headers in multimodal requests.
 * Values are escaped so embedded quotes, newlines, or angle brackets cannot break out of their
 * surrounding tag/header context.
 */
public final class DocumentMetadataFormatter {

  private DocumentMetadataFormatter() {}

  /** Returns {@code documentId="…" fileName="…" contentType="…"} with values escaped. */
  public static String formatAttributes(final ResolvedDocument document) {
    return attribute("documentId", document.getDocumentId())
        + " "
        + attribute("fileName", document.getFileName())
        + " "
        + attribute("contentType", document.getContentType());
  }

  private static String attribute(final String name, final String value) {
    return name + "=\"" + escape(value) + "\"";
  }

  /**
   * Neutralizes characters that could break the surrounding {@code name="value"} attribute or open
   * a forged tag in the prompt — backslash, double quote, control characters, and angle brackets.
   * Kept dependency-free; standard Apache Commons would otherwise add a runtime requirement on the
   * consumer's classpath.
   */
  private static String escape(final String value) {
    if (value == null) {
      return "";
    }
    final StringBuilder out = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      final char c = value.charAt(i);
      switch (c) {
        case '\\':
          out.append("\\\\");
          break;
        case '"':
          out.append("\\\"");
          break;
        case '\n':
          out.append("\\n");
          break;
        case '\r':
          out.append("\\r");
          break;
        case '\t':
          out.append("\\t");
          break;
        case '<':
          out.append("&lt;");
          break;
        case '>':
          out.append("&gt;");
          break;
        default:
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
      }
    }
    return out.toString();
  }
}
