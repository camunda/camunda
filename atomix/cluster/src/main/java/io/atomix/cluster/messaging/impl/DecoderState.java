/*
 * Copyright 2016-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.cluster.messaging.impl;

/** State transitions a decoder goes through as it is decoding an incoming message. */
public enum DecoderState {
  READ_TYPE,
  READ_MESSAGE_ID,
  READ_SENDER_VERSION,
  READ_SENDER_IP,
  READ_SENDER_PORT,
  READ_SUBJECT_LENGTH,
  READ_SUBJECT,
  READ_STATUS,
  READ_CONTENT_LENGTH,
  READ_CONTENT
}
