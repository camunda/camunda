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
package io.zeebe.logstreams.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.logstreams.util.RocksDBWrapper;
import io.zeebe.test.util.AutoCloseableRule;
import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

public class StateControllerTest {
  @Rule public TemporaryFolder tempFolderRule = new TemporaryFolder();
  @Rule public AutoCloseableRule autoCloseableRule = new AutoCloseableRule();

  private StateStorage storage;
  private StateController controller;

  @Before
  public void setup() throws IOException {
    final File snapshotsDirectory = tempFolderRule.newFolder("snapshots");
    final File runtimeDirectory = tempFolderRule.newFolder("runtime");
    storage = new StateStorage(runtimeDirectory, snapshotsDirectory);

    controller = new StateController();
    autoCloseableRule.manage(controller);
  }

  @Test
  public void shouldFailToOpenExistingDatabaseUnlessReopening() throws Exception {
    // given
    final File dbDir = storage.getRuntimeDirectory();

    // when
    controller.open(dbDir, false);
    controller.close();

    // then
    assertThatThrownBy(() -> controller.open(dbDir, false)).isInstanceOf(RocksDBException.class);

    // when
    controller.open(dbDir, true);
    assertThat(controller.isOpened()).isTrue();
  }

  @Test
  public void shouldCreateDatabaseIfNonExistent() throws Exception {
    // given
    final File dbDir = storage.getRuntimeDirectory();

    // when
    controller.open(dbDir, false);
    controller.close();

    // then
    assertThat(dbDir).exists(); // should test rather if the DB can be opened separately?
  }

  @Test
  public void shouldReopenPrexistingDatabase() throws Exception {
    // given
    final File dbDir = storage.getRuntimeDirectory();
    final RocksDBWrapper wrapper = new RocksDBWrapper();
    final String key = "test";
    final int value = 3;

    // when
    controller.open(dbDir, false);
    wrapper.wrap(controller.getDb());
    wrapper.putInt(key, value);
    controller.close();
    controller.open(dbDir, true);
    wrapper.wrap(controller.getDb());

    // then
    assertThat(wrapper.getInt(key)).isEqualTo(value);
  }

  @Test
  public void shouldDoNothingOnOpenIfAlreadyOpened() throws Exception {
    // given
    final File dbDir = storage.getRuntimeDirectory();

    // when
    controller.open(dbDir, false);

    // then
    assertThat(controller.isOpened()).isTrue();

    // given
    final RocksDB openedDB = controller.getDb();

    // then
    assertThat(controller.isOpened()).isTrue();
    assertThat(controller.open(dbDir, false)).isEqualTo(openedDB);
  }

  @Test
  public void shouldCloseAllResourcesOnClose() throws Exception {
    // given
    final File dbDir = storage.getRuntimeDirectory();

    // when
    final RocksDB openedDB = controller.open(dbDir, false);

    // then
    assertThat(controller.isOpened()).isTrue();
    assertThat(openedDB.isOwningHandle()).isTrue();

    // when
    controller.close();

    // then
    assertThat(controller.isOpened()).isFalse();
    assertThat(openedDB.isOwningHandle()).isFalse();
  }
}
