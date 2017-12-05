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
package io.zeebe.gossip.membership;

import java.util.*;

public class RoundRobinMemberIterator implements Iterator<Member>
{
    private final Random random = new Random();

    private final List<Member> members;

    private int index = 0;

    public RoundRobinMemberIterator(MembershipList list)
    {
        this.members = list.getMembersView();
    }

    @Override
    public boolean hasNext()
    {
        return members.size() > 0;
    }

    @Override
    public Member next()
    {
        if (hasNext())
        {
            index += 1;

            if (index >= members.size())
            {
                // TODO keep it mind that other's are may also watching the list
                Collections.shuffle(members, random);

                index = 0;
            }

            return members.get(index);
        }
        else
        {
            throw new NoSuchElementException();
        }
    }

}
