/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.rest.dto.incidents;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.operate.webapp.rest.dto.incidents.IncidentsByWorkflowGroupStatisticsDto;
import org.junit.Test;

public class IncidentByWorkflowGroupStatisticsTest {

  @Test
  public void testComparatorSameInstances() {
    IncidentsByWorkflowGroupStatisticsDto moreIncidents = newWithInstancesAndIncidents(5, 3);
    IncidentsByWorkflowGroupStatisticsDto lesserIncidents = newWithInstancesAndIncidents(5, 2);
    assertIsBefore(moreIncidents, lesserIncidents);
  }
  
  @Test
  public void testComparatorDifferentInstancesAndIncidents() {
    IncidentsByWorkflowGroupStatisticsDto moreIncidents= newWithInstancesAndIncidents(1314+845, 845);
    IncidentsByWorkflowGroupStatisticsDto lessIncidents= newWithInstancesAndIncidents(1351+831, 831);
    assertIsBefore(moreIncidents, lessIncidents);
  }
  
  @Test
  public void testComparatorZeroIncidents() {
    IncidentsByWorkflowGroupStatisticsDto moreInstances= newWithInstancesAndIncidents(172, 0);
    IncidentsByWorkflowGroupStatisticsDto lessInstances= newWithInstancesAndIncidents(114, 0);
    assertIsBefore(moreInstances, lessInstances);
  }
  
  @Test
  public void testComparatorSameIncidentsAndInstances() {
    IncidentsByWorkflowGroupStatisticsDto onlyOtherBPMN1= newWithInstancesAndIncidents(172, 0);
    onlyOtherBPMN1.setBpmnProcessId("1");
    IncidentsByWorkflowGroupStatisticsDto onlyOtherBPMN2= newWithInstancesAndIncidents(172, 0);
    onlyOtherBPMN2.setBpmnProcessId("2");
    assertIsBefore(onlyOtherBPMN1, onlyOtherBPMN2);
  }
  
  protected IncidentsByWorkflowGroupStatisticsDto newWithInstancesAndIncidents(int instances,int incidents) {
    IncidentsByWorkflowGroupStatisticsDto newObject = new IncidentsByWorkflowGroupStatisticsDto();
    newObject.setActiveInstancesCount(Long.valueOf(instances));
    newObject.setInstancesWithActiveIncidentsCount(incidents);
    return newObject;
  }
  
  protected void assertIsBefore(IncidentsByWorkflowGroupStatisticsDto first,IncidentsByWorkflowGroupStatisticsDto second) {
    assertThat(IncidentsByWorkflowGroupStatisticsDto.COMPARATOR.compare(first, second)).isLessThan(0);
  }
}
