/**
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
package io.zeebe.logstreams.integration.util;

import java.nio.ByteBuffer;

import io.zeebe.logstreams.impl.log.fs.FsLogStorage;
import io.zeebe.logstreams.impl.log.fs.FsLogStorageConfiguration;

public class ControllableFsLogStorage extends FsLogStorage
{
    private boolean failure = false;

    public ControllableFsLogStorage(FsLogStorageConfiguration cfg)
    {
        super(cfg);
    }

    @Override
    public long append(ByteBuffer buffer)
    {
        if (failure)
        {
            return -1;
        }
        else
        {
            return super.append(buffer);
        }
    }

    public void setFailure(boolean fail)
    {
        this.failure = fail;
    }

}
