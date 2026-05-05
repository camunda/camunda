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

public final class ResolvedDocumentImpl implements ResolvedDocument {

  private final String documentId;
  private final String fileName;
  private final String contentType;
  private final byte[] data;
  private final String errorMessage;

  public ResolvedDocumentImpl(
      final String documentId,
      final String fileName,
      final String contentType,
      final byte[] data,
      final String errorMessage) {
    this.documentId = documentId;
    this.fileName = fileName;
    this.contentType = contentType;
    this.data = data;
    this.errorMessage = errorMessage;
  }

  public static ResolvedDocument resolved(
      final String documentId, final String fileName, final String contentType, final byte[] data) {
    return new ResolvedDocumentImpl(documentId, fileName, contentType, data, null);
  }

  public static ResolvedDocument failed(
      final String documentId,
      final String fileName,
      final String contentType,
      final String errorMessage) {
    return new ResolvedDocumentImpl(documentId, fileName, contentType, null, errorMessage);
  }

  @Override
  public String getDocumentId() {
    return documentId;
  }

  @Override
  public String getFileName() {
    return fileName;
  }

  @Override
  public String getContentType() {
    return contentType;
  }

  @Override
  public byte[] getData() {
    return data;
  }

  @Override
  public String getErrorMessage() {
    return errorMessage;
  }

  @Override
  public boolean isResolved() {
    return errorMessage == null && data != null;
  }
}
