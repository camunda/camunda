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
package io.zeebe.protocol.impl.record.value.variable;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.DocumentProperty;
import io.zeebe.msgpack.property.EnumProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.protocol.VariableDocumentUpdateSemantic;
import java.util.Objects;
import org.agrona.DirectBuffer;

public class VariableDocumentRecord extends UnpackedObject {
  private final LongProperty scopeKeyProperty = new LongProperty("scopeKey");
  private final EnumProperty<VariableDocumentUpdateSemantic> updateSemanticsProperty =
      new EnumProperty<>(
          "updateSemantics",
          VariableDocumentUpdateSemantic.class,
          VariableDocumentUpdateSemantic.PROPAGATE);
  private final DocumentProperty documentProperty = new DocumentProperty("document");

  public VariableDocumentRecord() {
    this.declareProperty(scopeKeyProperty)
        .declareProperty(updateSemanticsProperty)
        .declareProperty(documentProperty);
  }

  public long getScopeKey() {
    return scopeKeyProperty.getValue();
  }

  public VariableDocumentRecord setScopeKey(long scopeKey) {
    scopeKeyProperty.setValue(scopeKey);
    return this;
  }

  public VariableDocumentUpdateSemantic getUpdateSemantics() {
    return updateSemanticsProperty.getValue();
  }

  public VariableDocumentRecord setUpdateSemantics(VariableDocumentUpdateSemantic updateSemantics) {
    updateSemanticsProperty.setValue(updateSemantics);
    return this;
  }

  public DirectBuffer getDocument() {
    return documentProperty.getValue();
  }

  public VariableDocumentRecord setDocument(DirectBuffer document) {
    documentProperty.setValue(document);
    return this;
  }

  public VariableDocumentRecord wrap(VariableDocumentRecord other) {
    this.setScopeKey(other.getScopeKey())
        .setDocument(other.getDocument())
        .setUpdateSemantics(other.getUpdateSemantics());

    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof VariableDocumentRecord)) {
      return false;
    }

    final VariableDocumentRecord that = (VariableDocumentRecord) o;
    return Objects.equals(scopeKeyProperty, that.scopeKeyProperty)
        && Objects.equals(updateSemanticsProperty, that.updateSemanticsProperty)
        && Objects.equals(documentProperty, that.documentProperty);
  }

  @Override
  public int hashCode() {
    return Objects.hash(scopeKeyProperty, updateSemanticsProperty, documentProperty);
  }

  @Override
  public String toString() {
    return "VariableDocumentRecord{"
        + "scopeKey="
        + getScopeKey()
        + ", mergeSemantics="
        + getUpdateSemantics()
        + ", document="
        + getDocument()
        + "} "
        + super.toString();
  }
}
