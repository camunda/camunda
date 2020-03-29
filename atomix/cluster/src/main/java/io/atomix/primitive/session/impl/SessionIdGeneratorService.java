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
package io.atomix.primitive.session.impl;

import io.atomix.primitive.service.AbstractPrimitiveService;
import io.atomix.primitive.service.BackupInput;
import io.atomix.primitive.service.BackupOutput;
import io.atomix.primitive.service.ServiceExecutor;
import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.Serializer;

/** ID generator service. */
public class SessionIdGeneratorService extends AbstractPrimitiveService {

  private static final Serializer SERIALIZER =
      Serializer.using(
          Namespace.builder().register(SessionIdGeneratorOperations.NAMESPACE).build());

  private long id;

  public SessionIdGeneratorService() {
    super(SessionIdGeneratorType.instance());
  }

  @Override
  public Serializer serializer() {
    return SERIALIZER;
  }

  @Override
  protected void configure(final ServiceExecutor executor) {
    executor.register(SessionIdGeneratorOperations.NEXT, this::next);
  }

  @Override
  public void backup(final BackupOutput writer) {
    writer.writeLong(id);
  }

  @Override
  public void restore(final BackupInput reader) {
    id = reader.readLong();
  }

  /**
   * Returns the next session ID.
   *
   * @return the next session ID
   */
  protected long next() {
    return ++id;
  }
}
