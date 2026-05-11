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

import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.process.test.api.judge.ResolvedDocument;

public final class ResolvedDocumentImpl implements ResolvedDocument {

  private final DocumentReferenceResponse reference;
  private final byte[] content;
  private final String errorMessage;

  public ResolvedDocumentImpl(
      final DocumentReferenceResponse reference, final byte[] content, final String errorMessage) {
    this.reference = reference;
    this.content = content;
    this.errorMessage = errorMessage;
  }

  public static ResolvedDocument resolved(
      final DocumentReferenceResponse reference, final byte[] content) {
    return new ResolvedDocumentImpl(reference, content, null);
  }

  public static ResolvedDocument failed(
      final DocumentReferenceResponse reference, final String errorMessage) {
    return new ResolvedDocumentImpl(reference, null, errorMessage);
  }

  @Override
  public DocumentReferenceResponse getReference() {
    return reference;
  }

  @Override
  public byte[] getContent() {
    return content;
  }

  @Override
  public String getErrorMessage() {
    return errorMessage;
  }

  @Override
  public boolean isResolved() {
    return errorMessage == null && content != null;
  }
}
