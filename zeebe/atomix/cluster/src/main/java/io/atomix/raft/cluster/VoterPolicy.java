/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft.cluster;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The voter policy of a Raft configuration: the number of voters ({@link RaftMember.Type#ACTIVE}
 * members) for each zone, in the order of the zones' priority. The zone of a member is {@link
 * io.atomix.cluster.MemberId#zone()}.
 *
 * <p>Raft stores the policy with the configuration and changes it with a configuration change. The
 * leader rejects a change that puts more voters into a zone than the policy allows. Everything else
 * about the policy (who is promoted or demoted) is decided outside of Raft.
 *
 * @param zones the zones with their number of voters, highest priority first
 */
public record VoterPolicy(List<ZoneVoters> zones) {

  public VoterPolicy {
    requireNonNull(zones, "zones cannot be null");
    if (zones.isEmpty()) {
      throw new IllegalArgumentException("A voter policy must have at least one zone");
    }
    // A mutable copy, because Kryo cannot deserialize immutable collections
    zones = new ArrayList<>(zones);
  }

  public static VoterPolicy of(final ZoneVoters... zones) {
    return new VoterPolicy(List.of(zones));
  }

  /**
   * @return the number of voters of the zone, or empty if the zone is not part of the policy
   */
  public Optional<Integer> votersOf(final String zone) {
    return zones.stream()
        .filter(zoneVoters -> zoneVoters.zone().equals(zone))
        .map(ZoneVoters::voters)
        .findFirst();
  }

  /**
   * @param zone the zone, as in {@link io.atomix.cluster.MemberId#zone()}
   * @param voters the number of voters of the zone
   */
  public record ZoneVoters(String zone, int voters) {
    public ZoneVoters {
      requireNonNull(zone, "zone cannot be null");
      if (voters < 0) {
        throw new IllegalArgumentException("voters must not be negative");
      }
    }
  }
}
