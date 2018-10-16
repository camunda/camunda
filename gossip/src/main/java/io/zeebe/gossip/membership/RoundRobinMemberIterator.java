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

import io.zeebe.gossip.GossipMembershipListener;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import org.agrona.collections.IntArrayList;

public class RoundRobinMemberIterator implements Iterator<Member>, GossipMembershipListener {
  private final Random random = new Random();

  private final List<Member> members;

  private final IntArrayList playList = new IntArrayList();
  private int index = 0;

  public RoundRobinMemberIterator(MembershipList list) {
    this.members = list.getMembersView();

    list.addListener(this);
  }

  @Override
  public boolean hasNext() {
    return playList.size() > 0;
  }

  @Override
  public Member next() {
    if (hasNext()) {
      if (index >= playList.size()) {
        if (playList.size() > 2) {
          Collections.shuffle(playList, random);
        }

        index = 0;
      }

      final int nextMember = playList.getInt(index);
      index += 1;

      return members.get(nextMember);
    } else {
      throw new NoSuchElementException();
    }
  }

  @Override
  public void onAdd(Member member) {
    final int i = members.indexOf(member);

    if (index < playList.size()) {
      playList.addInt(index, i);
    } else {
      playList.addInt(i);
    }
  }

  @Override
  public void onRemove(Member member) {
    final int i = members.indexOf(member);

    playList.removeInt(i);
  }
}
