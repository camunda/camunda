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
package io.atomix.raft.partition.systempartition;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The voter policy of the system partition (design 5.1): the number of voters ({@link
 * io.atomix.raft.cluster.RaftMember.Type#ACTIVE} members) for each zone, in the order of the zones
 * for the election priority. The first zone is the control zone, if the cluster has one. The zone
 * of a member is {@link io.atomix.cluster.MemberId#zone()}, or "" for a member without a zone.
 *
 * <p>Raft does not know the policy (D18). The membership component uses it to decide promotions and
 * demotions, and the recovery uses it to limit the voters of a forced configuration.
 */
public final class VoterPolicy {

  private final List<ZoneVoters> zones;

  /**
   * @param zones the zones with their number of voters, highest priority first
   */
  public VoterPolicy(final List<ZoneVoters> zones) {
    requireNonNull(zones, "zones cannot be null");
    if (zones.isEmpty()) {
      throw new IllegalArgumentException("A voter policy must have at least one zone");
    }
    this.zones = List.copyOf(zones);
  }

  public List<ZoneVoters> zones() {
    return zones;
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

  @Override
  public boolean equals(final Object o) {
    return o instanceof final VoterPolicy other && zones.equals(other.zones);
  }

  @Override
  public int hashCode() {
    return zones.hashCode();
  }

  @Override
  public String toString() {
    return "VoterPolicy[zones=" + zones + "]";
  }

  /** The number of voters of a zone. Not a record, for the same reason as {@link VoterPolicy}. */
  public static final class ZoneVoters {

    private final String zone;
    private final int voters;

    /**
     * @param zone the zone, as in {@link io.atomix.cluster.MemberId#zone()}
     * @param voters the number of voters of the zone
     */
    public ZoneVoters(final String zone, final int voters) {
      this.zone = requireNonNull(zone, "zone cannot be null");
      if (voters < 0) {
        throw new IllegalArgumentException("voters must not be negative");
      }
      this.voters = voters;
    }

    public String zone() {
      return zone;
    }

    public int voters() {
      return voters;
    }

    @Override
    public boolean equals(final Object o) {
      return o instanceof final ZoneVoters other
          && zone.equals(other.zone)
          && voters == other.voters;
    }

    @Override
    public int hashCode() {
      return Objects.hash(zone, voters);
    }

    @Override
    public String toString() {
      return "ZoneVoters[zone=" + zone + ", voters=" + voters + "]";
    }
  }
}
