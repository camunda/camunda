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
import java.util.List;

/**
 * Builds the {@code <resolved_documents>} section that is appended to the judge prompt when Camunda
 * documents have been resolved. The section identifies each document by its {@code documentId} so
 * the judge can match it back to the reference inside {@code <actual_value>}.
 *
 * <p>This decoration is only used by the multimodal call path. The actual binary content is
 * attached separately as structured content blocks by the {@link
 * io.camunda.process.test.api.judge.MultimodalChatModelAdapter} implementation; the section built
 * here only describes the documents textually so the judge can refer to them by id.
 */
public final class ResolvedDocumentPromptSection {

  private static final String SECTION_OPEN = "<resolved_documents>";
  private static final String SECTION_CLOSE = "</resolved_documents>";
  private static final String PREAMBLE =
      "The asserted value contained the following Camunda document references. Their binary "
          + "content is attached as additional content blocks in this conversation. Each block "
          + "is identified by its documentId so you can match it back to the reference inside "
          + "<actual_value>.";

  private ResolvedDocumentPromptSection() {}

  /**
   * @return the rendered section, or {@code null} if {@code documents} is empty
   */
  public static String render(final List<ResolvedDocument> documents) {
    if (documents == null || documents.isEmpty()) {
      return null;
    }
    final StringBuilder section = new StringBuilder();
    section.append(SECTION_OPEN).append('\n').append(PREAMBLE).append("\n\n");
    for (final ResolvedDocument doc : documents) {
      section.append(describe(doc)).append('\n');
    }
    section.append(SECTION_CLOSE);
    return section.toString();
  }

  private static String describe(final ResolvedDocument doc) {
    final StringBuilder line = new StringBuilder();
    line.append("- ");
    appendAttribute(line, "documentId", doc.getDocumentId());
    appendAttribute(line, " fileName", doc.getFileName());
    appendAttribute(line, " contentType", doc.getContentType());
    if (!doc.isResolved()) {
      appendAttribute(line, " status", "unresolved");
      appendAttribute(line, " error", doc.getErrorMessage());
    }
    return line.toString();
  }

  private static void appendAttribute(
      final StringBuilder out, final String name, final String value) {
    out.append(name).append("=\"").append(value == null ? "" : value).append('"');
  }
}
