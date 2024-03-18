/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.util;

import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.zeebe.ImportValueType;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class SearchTestRule extends TestWatcher {

  protected static final Logger LOGGER = LoggerFactory.getLogger(SearchTestRule.class);

  @Autowired protected SearchTestRuleProvider searchTestRuleProvider;

  @Autowired protected OperateProperties operateProperties;

  Consumer<OperateProperties> operatePropertiesCustomizer = operateProperties -> {};

  public SearchTestRule() {}

  public SearchTestRule(String indexPrefix) {
    searchTestRuleProvider.setIndexPrefix(indexPrefix);
  }

  public SearchTestRule(Consumer<OperateProperties> operatePropertiesCustomizer) {
    this.operatePropertiesCustomizer = operatePropertiesCustomizer;
  }

  @Override
  protected void failed(Throwable e, Description description) {
    super.failed(e, description);
    searchTestRuleProvider.failed(e, description);
  }

  @Override
  protected void starting(Description description) {
    operatePropertiesCustomizer.accept(operateProperties);
    searchTestRuleProvider.starting(description);
  }

  @Override
  protected void finished(Description description) {
    searchTestRuleProvider.finished(description);
  }

  public void refreshSerchIndexes() {
    searchTestRuleProvider.refreshSearchIndices();
  }

  public void refreshZeebeIndices() {
    searchTestRuleProvider.refreshZeebeIndices();
  }

  public void refreshOperateSearchIndices() {
    searchTestRuleProvider.refreshOperateSearchIndices();
  }

  public void processAllRecordsAndWait(
      Integer maxWaitingRounds, Predicate<Object[]> predicate, Object... arguments) {
    searchTestRuleProvider.processAllRecordsAndWait(maxWaitingRounds, predicate, arguments);
  }

  public void processAllRecordsAndWait(Predicate<Object[]> predicate, Object... arguments) {
    searchTestRuleProvider.processAllRecordsAndWait(predicate, arguments);
  }

  public void processAllRecordsAndWait(
      Predicate<Object[]> predicate, Supplier<Object> supplier, Object... arguments) {
    searchTestRuleProvider.processAllRecordsAndWait(predicate, supplier, arguments);
  }

  public void processAllRecordsAndWait(
      boolean runPostImport,
      Predicate<Object[]> predicate,
      Supplier<Object> supplier,
      Object... arguments) {
    searchTestRuleProvider.processAllRecordsAndWait(runPostImport, predicate, supplier, arguments);
  }

  public void processRecordsWithTypeAndWait(
      ImportValueType importValueType, Predicate<Object[]> predicate, Object... arguments) {
    searchTestRuleProvider.processRecordsWithTypeAndWait(importValueType, predicate, arguments);
  }

  public void processRecordsWithTypeAndWait(
      ImportValueType importValueType,
      boolean runPostImport,
      Predicate<Object[]> predicate,
      Object... arguments) {
    searchTestRuleProvider.processRecordsWithTypeAndWait(
        importValueType, runPostImport, predicate, arguments);
  }

  public void persistNew(OperateEntity... entitiesToPersist) {
    searchTestRuleProvider.persistNew(entitiesToPersist);
  }

  public void persistOperateEntitiesNew(List<? extends OperateEntity> operateEntities)
      throws PersistenceException {
    searchTestRuleProvider.persistOperateEntitiesNew(operateEntities);
  }

  public Map<Class<? extends OperateEntity>, String> getEntityToAliasMap() {
    return searchTestRuleProvider.getEntityToAliasMap();
  }

  public boolean indexExists(String index) throws IOException {
    return searchTestRuleProvider.indexExists(index);
  }
}
