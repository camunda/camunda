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

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.camunda.process.test.api.judge.MultimodalChatModelAdapter;
import io.camunda.process.test.api.judge.ResolvedDocument;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapter that wraps a LangChain4j {@link ChatModel} as a {@link MultimodalChatModelAdapter} so the
 * judge can attach Camunda document binary content as structured content blocks.
 */
public final class LangChain4jChatModelAdapter implements MultimodalChatModelAdapter {

  private static final Logger LOG = LoggerFactory.getLogger(LangChain4jChatModelAdapter.class);

  private final ChatModel delegate;

  public LangChain4jChatModelAdapter(final ChatModel delegate) {
    this.delegate = delegate;
  }

  @Override
  public String generate(final String prompt) {
    return delegate.chat(prompt);
  }

  @Override
  public String generate(final String prompt, final List<ResolvedDocument> documents) {
    if (documents == null || documents.isEmpty()) {
      return delegate.chat(prompt);
    }

    final List<Content> parts = new ArrayList<>();
    parts.add(TextContent.from(prompt));
    parts.add(
        TextContent.from(
            "Below are the Camunda document references found inside <actual_value>, resolved to "
                + "their content. Each block is identified by its documentId so you can match it "
                + "back to the reference in <actual_value>."));

    for (final ResolvedDocument document : documents) {
      parts.addAll(toContentParts(document));
    }

    final List<ChatMessage> messages = new ArrayList<>();
    messages.add(UserMessage.from(parts));

    final ChatResponse response = delegate.chat(messages);
    return response.aiMessage().text();
  }

  private static List<Content> toContentParts(final ResolvedDocument document) {
    final List<Content> parts = new ArrayList<>();
    parts.add(
        TextContent.from("--- " + DocumentMetadataFormatter.formatAttributes(document) + " ---"));

    final String contentType = document.getContentType();
    final byte[] content = document.getContent();
    switch (DocumentKind.from(contentType)) {
      case IMAGE:
        parts.add(ImageContent.from(Base64.getEncoder().encodeToString(content), contentType));
        break;
      case PDF:
        parts.add(
            PdfFileContent.from(
                PdfFile.builder()
                    .base64Data(Base64.getEncoder().encodeToString(content))
                    .mimeType(contentType)
                    .build()));
        break;
      case TEXT:
        parts.add(TextContent.from(new String(content, StandardCharsets.UTF_8)));
        break;
      case OTHER:
      default:
        LOG.warn(
            "Document '{}' has content type '{}' which is not natively supported as a content "
                + "block; including a placeholder text marker only",
            document.getDocumentId(),
            contentType);
        parts.add(
            TextContent.from(
                "[binary content of type "
                    + (contentType == null ? "unknown" : contentType)
                    + " omitted; the LLM provider does not support this content type as a block]"));
        break;
    }
    return parts;
  }

  private enum DocumentKind {
    IMAGE,
    PDF,
    TEXT,
    OTHER;

    static DocumentKind from(final String contentType) {
      if (contentType == null) {
        return OTHER;
      }
      final String ct = contentType.toLowerCase();
      if (ct.startsWith("image/")) {
        return IMAGE;
      }
      if ("application/pdf".equals(ct)) {
        return PDF;
      }
      if (ct.startsWith("text/")
          || "application/json".equals(ct)
          || "application/xml".equals(ct)
          || "application/yaml".equals(ct)
          || "application/x-yaml".equals(ct)) {
        return TEXT;
      }
      return OTHER;
    }
  }
}
