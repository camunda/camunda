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
package io.zeebe.map.iterator;

import io.zeebe.map.KeyHandler;
import io.zeebe.map.ValueHandler;

/**
 * Is used in combination with an iterator to store an entry of the map. Should
 * provide getter methods for the stored key and value.
 */
public interface ZbMapEntry<K extends KeyHandler, V extends ValueHandler>
{
    /**
     * Read the current key and value from the given handlers.
     */
    void read(K keyHander, V valueHandler);

}
