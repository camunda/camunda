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
package io.camunda.client.impl.response;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.camunda.client.api.response.DocumentMetadata;
import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.client.impl.response.DocumentReferenceResponseImpl.DocumentReferenceDeserializer;
import io.camunda.client.impl.response.DocumentReferenceResponseImpl.DocumentReferenceSerializer;
import io.camunda.client.protocol.rest.DocumentReference;
import java.io.IOException;

@JsonSerialize(using = DocumentReferenceSerializer.class)
@JsonDeserialize(using = DocumentReferenceDeserializer.class)
public class DocumentReferenceResponseImpl implements DocumentReferenceResponse {
  private final DocumentReference documentReference;
  private final String documentId;
  private final String storeId;
  private final String contentHash;
  private final DocumentMetadata metadata;

  public DocumentReferenceResponseImpl(final DocumentReference documentReference) {
    this.documentReference = documentReference;
    documentId = documentReference.getDocumentId();
    storeId = documentReference.getStoreId();
    contentHash = documentReference.getContentHash();

    metadata = new DocumentMetadataImpl(documentReference.getMetadata());
  }

  @Override
  public String getDocumentId() {
    return documentId;
  }

  @Override
  public String getStoreId() {
    return storeId;
  }

  @Override
  public String getContentHash() {
    return contentHash;
  }

  @Override
  public DocumentMetadata getMetadata() {
    return metadata;
  }

  public DocumentReference getDocumentReference() {
    return documentReference;
  }

  public static class DocumentReferenceSerializer
      extends StdSerializer<DocumentReferenceResponseImpl> {

    public DocumentReferenceSerializer() {
      this(null);
    }

    public DocumentReferenceSerializer(final Class<DocumentReferenceResponseImpl> t) {
      super(t);
    }

    @Override
    public void serialize(
        final DocumentReferenceResponseImpl value,
        final JsonGenerator gen,
        final SerializerProvider provider)
        throws IOException {
      gen.writeObject(value.getDocumentReference());
    }
  }

  public static class DocumentReferenceDeserializer
      extends StdDeserializer<DocumentReferenceResponseImpl> {

    public DocumentReferenceDeserializer() {
      this(null);
    }

    public DocumentReferenceDeserializer(final Class<DocumentReferenceResponseImpl> t) {
      super(t);
    }

    @Override
    public DocumentReferenceResponseImpl deserialize(
        final JsonParser p, final DeserializationContext ctxt)
        throws IOException, JacksonException {
      final DocumentReference documentReference =
          p.getCodec().readValue(p, DocumentReference.class);
      return new DocumentReferenceResponseImpl(documentReference);
    }
  }
}
