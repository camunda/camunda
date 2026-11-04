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
package io.camunda.client.impl.http;

import org.apache.hc.core5.http.HttpStreamResetException;

/**
 * Ends the download of a document whose stream the caller closed before the end of the document. It
 * is a {@link HttpStreamResetException}, the exception httpcore5 uses to end a single response
 * early. {@link HttpRetryStrategy} keeps httpclient5 from sending the request again.
 */
final class DocumentStreamClosedException extends HttpStreamResetException {

  DocumentStreamClosedException() {
    super("Document stream was closed before the response was fully received");
  }
}
