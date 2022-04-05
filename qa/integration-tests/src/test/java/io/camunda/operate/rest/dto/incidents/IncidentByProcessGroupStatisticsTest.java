/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.rest.dto.incidents;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.webapp.rest.dto.incidents.IncidentsByProcessGroupStatisticsDto;
import org.junit.Test;

public class IncidentByProcessGroupStatisticsTest {

  @Test
  public void testComparatorSameInstances() {
    IncidentsByProcessGroupStatisticsDto moreIncidents = newWithInstancesAndIncidents(5, 3);
    IncidentsByProcessGroupStatisticsDto lesserIncidents = newWithInstancesAndIncidents(5, 2);
    assertIsBefore(moreIncidents, lesserIncidents);
  }
  
  @Test
  public void testComparatorDifferentInstancesAndIncidents() {
    IncidentsByProcessGroupStatisticsDto moreIncidents= newWithInstancesAndIncidents(1314+845, 845);
    IncidentsByProcessGroupStatisticsDto lessIncidents= newWithInstancesAndIncidents(1351+831, 831);
    assertIsBefore(moreIncidents, lessIncidents);
  }
  
  @Test
  public void testComparatorZeroIncidents() {
    IncidentsByProcessGroupStatisticsDto moreInstances= newWithInstancesAndIncidents(172, 0);
    IncidentsByProcessGroupStatisticsDto lessInstances= newWithInstancesAndIncidents(114, 0);
    assertIsBefore(moreInstances, lessInstances);
  }
  
  @Test
  public void testComparatorSameIncidentsAndInstances() {
    IncidentsByProcessGroupStatisticsDto onlyOtherBPMN1= newWithInstancesAndIncidents(172, 0);
    onlyOtherBPMN1.setBpmnProcessId("1");
    IncidentsByProcessGroupStatisticsDto onlyOtherBPMN2= newWithInstancesAndIncidents(172, 0);
    onlyOtherBPMN2.setBpmnProcessId("2");
    assertIsBefore(onlyOtherBPMN1, onlyOtherBPMN2);
  }
  
  protected IncidentsByProcessGroupStatisticsDto newWithInstancesAndIncidents(int instances,int incidents) {
    IncidentsByProcessGroupStatisticsDto newObject = new IncidentsByProcessGroupStatisticsDto();
    newObject.setActiveInstancesCount(Long.valueOf(instances));
    newObject.setInstancesWithActiveIncidentsCount(incidents);
    return newObject;
  }
  
  protected void assertIsBefore(IncidentsByProcessGroupStatisticsDto first,IncidentsByProcessGroupStatisticsDto second) {
    assertThat(IncidentsByProcessGroupStatisticsDto.COMPARATOR.compare(first, second)).isLessThan(0);
  }
}
