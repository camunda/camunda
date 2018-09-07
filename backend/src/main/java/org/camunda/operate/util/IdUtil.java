/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.operate.util;

public abstract class IdUtil {

  public static String createId(long key, int partitionId) {
    return String.valueOf(key) + "-" + partitionId;
  }

  public static long extractKey(String id) {
    final int i = id.indexOf("-");
    if (i == -1) {
      return Long.valueOf(id);
    }
    return Long.valueOf(id.substring(0, i));
  }

  public static int extractPartitionId(String id) {
    final int i = id.indexOf("-");
    if (i == -1) {
      return 0;
    }
    return Integer.valueOf(id.substring(i + 1, id.length()));
  }

}
