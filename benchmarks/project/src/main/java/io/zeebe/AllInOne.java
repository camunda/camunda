/*
 * Copyright © 2019 camunda services GmbH (info@camunda.com)
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
package io.zeebe;

import io.zeebe.config.AppCfg;
import java.util.Arrays;
import java.util.List;

public class AllInOne extends App {

  private final AppCfg appCfg;

  private AllInOne(AppCfg appCfg) {
    this.appCfg = appCfg;
  }

  @Override
  public void run() {
    final List<Thread> threads =
        Arrays.asList(new Thread(new Starter(appCfg)), new Thread(new Worker(appCfg)));

    for (Thread thread : threads) {
      thread.start();
    }

    for (Thread thread : threads) {
      try {
        thread.join();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  public static void main(String[] args) {
    createApp(AllInOne::new);
  }
}
