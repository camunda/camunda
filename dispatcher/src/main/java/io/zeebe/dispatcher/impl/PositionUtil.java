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
package io.zeebe.dispatcher.impl;

/** Utility for composing the position */
public class PositionUtil {

  public static long position(int partitionId, int partitionOffset) {
    return ((long) partitionId) << 32 | partitionOffset & 0xFFFFFFFFL;
  }

  public static int partitionId(long position) {
    return (int) (position >> 32);
  }

  public static int partitionOffset(long position) {
    return (int) (position & 0xFFFFFFFFL);
  }
}
