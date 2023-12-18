/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.util;

import io.camunda.tasklist.zeebe.ImportValueType;
import io.camunda.tasklist.zeebeimport.RecordsReader;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.extension.Extension;

public interface DatabaseTestExtension extends Extension {

  public void assertMaxOpenScrollContexts(final int maxOpenScrollContexts);

  public void refreshIndexesInElasticsearch();

  public void refreshZeebeIndices();

  public void refreshTasklistIndices();

  public void processAllRecordsAndWait(TestCheck testCheck, Object... arguments);

  public void processAllRecordsAndWait(
      TestCheck testCheck, Supplier<Object> supplier, Object... arguments);

  public void processRecordsWithTypeAndWait(
      ImportValueType importValueType, TestCheck testCheck, Object... arguments);

  public void processRecordsAndWaitFor(
      Collection<RecordsReader> readers,
      TestCheck testCheck,
      Supplier<Object> supplier,
      Object... arguments);

  public boolean areIndicesCreatedAfterChecks(
      String indexPrefix, int minCountOfIndices, int maxChecks);

  public List<RecordsReader> getRecordsReaders(ImportValueType importValueType);

  public int getOpenScrollcontextSize();
}
