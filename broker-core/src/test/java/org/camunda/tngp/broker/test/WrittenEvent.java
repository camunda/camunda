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
package org.camunda.tngp.broker.test;

import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;

public class WrittenEvent<E>
{
    final long key;
    final E value;
    final BrokerEventMetadata metadata;

    public WrittenEvent(long key, E value, BrokerEventMetadata metadata)
    {
        this.key = key;
        this.value = value;
        this.metadata = metadata;
    }

    public long getKey()
    {
        return key;
    }

    public E getValue()
    {
        return value;
    }

    public BrokerEventMetadata getMetadata()
    {
        return metadata;
    }

}