/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.rest.dto.incidents;

import org.junit.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class IncidentByWorkflowStatisticsDtoTest {

  @Test
  public void testComparatorSameInstances() {
     IncidentByWorkflowStatisticsDto moreIncidents = newWithInstancesAndIncidents(5,3);
     IncidentByWorkflowStatisticsDto lesserIncidents = newWithInstancesAndIncidents(5, 2);
     assertIsBefore(moreIncidents, lesserIncidents);
  }
  
  @Test
  public void testComparatorNoInstances() {
     IncidentByWorkflowStatisticsDto worfklowVersionOne = new IncidentByWorkflowStatisticsDto();
     worfklowVersionOne.setVersion(1);
     IncidentByWorkflowStatisticsDto worfklowVersionThree =  new IncidentByWorkflowStatisticsDto();
     worfklowVersionThree.setVersion(3);
     IncidentByWorkflowStatisticsDto workflowVersionFour = new IncidentByWorkflowStatisticsDto();
     workflowVersionFour.setVersion(4);
     assertIsBefore(worfklowVersionOne, worfklowVersionThree);
     assertIsBefore(worfklowVersionThree, workflowVersionFour);
  }
  
  @Test
  public void testComparatorDifferentInstancesAndIncidents() {
     IncidentByWorkflowStatisticsDto moreIncidents= newWithInstancesAndIncidents(1314+845, 845);
     IncidentByWorkflowStatisticsDto lessIncidents= newWithInstancesAndIncidents(1351+831, 831);
     assertIsBefore(moreIncidents, lessIncidents);
  }
  
  @Test
  public void testComparatorZeroIncidents() {
     IncidentByWorkflowStatisticsDto moreInstances= newWithInstancesAndIncidents(172, 0);
     IncidentByWorkflowStatisticsDto lessInstances= newWithInstancesAndIncidents(114, 0);
     assertIsBefore(moreInstances, lessInstances);
  }
  
  @Test
  public void testComparatorSameIncidentsAndInstances() {
     IncidentByWorkflowStatisticsDto onlyOtherBPMN1= newWithInstancesAndIncidents(172, 0);
     onlyOtherBPMN1.setBpmnProcessId("1");
     IncidentByWorkflowStatisticsDto onlyOtherBPMN2= newWithInstancesAndIncidents(172, 0);
     onlyOtherBPMN2.setBpmnProcessId("2");
     assertIsBefore(onlyOtherBPMN1, onlyOtherBPMN2);
  }
  
  protected IncidentByWorkflowStatisticsDto newWithInstancesAndIncidents(int instances,int incidents) {
    IncidentByWorkflowStatisticsDto newObject = new IncidentByWorkflowStatisticsDto();
    newObject.setActiveInstancesCount(new Long(instances));
    newObject.setInstancesWithActiveIncidentsCount(incidents);
    return newObject;
  }
  
  protected void assertIsBefore(IncidentByWorkflowStatisticsDto first,IncidentByWorkflowStatisticsDto second) {
    assertThat(IncidentByWorkflowStatisticsDto.COMPARATOR.compare(first, second)).isLessThan(0);
  }

}
