/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.rest.dto.incidents;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.webapp.rest.dto.incidents.IncidentByProcessStatisticsDto;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

public class IncidentByProcessStatisticsDtoTest {

  @Test
  public void testComparatorSameInstances() {
    final IncidentByProcessStatisticsDto moreIncidents = newWithInstancesAndIncidents(5, 3);
    final IncidentByProcessStatisticsDto lesserIncidents = newWithInstancesAndIncidents(5, 2);
    assertIsBefore(moreIncidents, lesserIncidents);
  }

  @Test
  public void testComparatorNoInstances() {
    final IncidentByProcessStatisticsDto worfklowVersionOne = new IncidentByProcessStatisticsDto();
    worfklowVersionOne.setVersion(1);
    final IncidentByProcessStatisticsDto worfklowVersionThree =
        new IncidentByProcessStatisticsDto();
    worfklowVersionThree.setVersion(3);
    final IncidentByProcessStatisticsDto processVersionFour = new IncidentByProcessStatisticsDto();
    processVersionFour.setVersion(4);

    assertIsBefore(worfklowVersionOne, worfklowVersionThree);
    assertIsBefore(worfklowVersionThree, processVersionFour);

    // and with TreeSet
    final Set<IncidentByProcessStatisticsDto> processes =
        new TreeSet<>(IncidentByProcessStatisticsDto.COMPARATOR);
    processes.add(worfklowVersionThree);
    processes.add(worfklowVersionOne);
    processes.add(processVersionFour);

    final Iterator<IncidentByProcessStatisticsDto> processesIterator = processes.iterator();
    assertThat(processesIterator.next().getVersion()).isEqualTo(1);
    assertThat(processesIterator.next().getVersion()).isEqualTo(3);
    assertThat(processesIterator.next().getVersion()).isEqualTo(4);
  }

  @Test
  public void testComparatorDifferentInstancesAndIncidents() {
    final IncidentByProcessStatisticsDto moreIncidents =
        newWithInstancesAndIncidents(1314 + 845, 845);
    final IncidentByProcessStatisticsDto lessIncidents =
        newWithInstancesAndIncidents(1351 + 831, 831);
    assertIsBefore(moreIncidents, lessIncidents);
  }

  @Test
  public void testComparatorZeroIncidents() {
    final IncidentByProcessStatisticsDto moreInstances = newWithInstancesAndIncidents(172, 0);
    final IncidentByProcessStatisticsDto lessInstances = newWithInstancesAndIncidents(114, 0);
    assertIsBefore(moreInstances, lessInstances);
  }

  @Test
  public void testComparatorSameIncidentsAndInstances() {
    final IncidentByProcessStatisticsDto onlyOtherBPMN1 = newWithInstancesAndIncidents(172, 0);
    onlyOtherBPMN1.setBpmnProcessId("1");
    final IncidentByProcessStatisticsDto onlyOtherBPMN2 = newWithInstancesAndIncidents(172, 0);
    onlyOtherBPMN2.setBpmnProcessId("2");
    assertIsBefore(onlyOtherBPMN1, onlyOtherBPMN2);
  }

  protected IncidentByProcessStatisticsDto newWithInstancesAndIncidents(
      final int instances, final int incidents) {
    final IncidentByProcessStatisticsDto newObject = new IncidentByProcessStatisticsDto();
    newObject.setActiveInstancesCount(Long.valueOf(instances));
    newObject.setInstancesWithActiveIncidentsCount(incidents);
    return newObject;
  }

  protected void assertIsBefore(
      final IncidentByProcessStatisticsDto first, final IncidentByProcessStatisticsDto second) {
    assertThat(IncidentByProcessStatisticsDto.COMPARATOR.compare(first, second)).isLessThan(0);
  }
}
