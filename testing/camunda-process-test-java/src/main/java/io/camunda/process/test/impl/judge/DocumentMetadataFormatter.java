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
import org.apache.commons.text.StringEscapeUtils;

/**
 * Renders the metadata attribute string used to identify a resolved document, both inside the
 * {@code <resolved_documents>} prompt section and inside per-block headers in multimodal requests.
 * Values are escaped so embedded quotes or angle brackets cannot break out of their surrounding
 * tag/header context.
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
    return name + "=\"" + escape(value == null ? "" : value) + "\"";
  }

  private static String escape(final String value) {
    return StringEscapeUtils.escapeJava(value).replace("<", "&lt;").replace(">", "&gt;");
  }
}
