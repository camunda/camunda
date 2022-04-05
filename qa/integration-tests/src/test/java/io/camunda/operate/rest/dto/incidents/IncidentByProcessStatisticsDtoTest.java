/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.rest.dto.incidents;

import io.camunda.operate.webapp.rest.dto.incidents.IncidentByProcessStatisticsDto;
import org.junit.Test;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

public class IncidentByProcessStatisticsDtoTest {

  @Test
  public void testComparatorSameInstances() {
     IncidentByProcessStatisticsDto moreIncidents = newWithInstancesAndIncidents(5,3);
     IncidentByProcessStatisticsDto lesserIncidents = newWithInstancesAndIncidents(5, 2);
     assertIsBefore(moreIncidents, lesserIncidents);
  }
  
  @Test
  public void testComparatorNoInstances() {
     IncidentByProcessStatisticsDto worfklowVersionOne = new IncidentByProcessStatisticsDto();
     worfklowVersionOne.setVersion(1);
     IncidentByProcessStatisticsDto worfklowVersionThree =  new IncidentByProcessStatisticsDto();
     worfklowVersionThree.setVersion(3);
     IncidentByProcessStatisticsDto processVersionFour = new IncidentByProcessStatisticsDto();
     processVersionFour.setVersion(4);
    
     assertIsBefore(worfklowVersionOne, worfklowVersionThree);
     assertIsBefore(worfklowVersionThree, processVersionFour);
     
     // and with TreeSet 
     Set<IncidentByProcessStatisticsDto> processes = new TreeSet<>(IncidentByProcessStatisticsDto.COMPARATOR);
     processes.add(worfklowVersionThree);
     processes.add(worfklowVersionOne);
     processes.add(processVersionFour);
     
     Iterator<IncidentByProcessStatisticsDto> processesIterator  = processes.iterator();
     assertThat(processesIterator.next().getVersion()).isEqualTo(1);
     assertThat(processesIterator.next().getVersion()).isEqualTo(3);
     assertThat(processesIterator.next().getVersion()).isEqualTo(4);
  }
  
  @Test
  public void testComparatorDifferentInstancesAndIncidents() {
     IncidentByProcessStatisticsDto moreIncidents= newWithInstancesAndIncidents(1314+845, 845);
     IncidentByProcessStatisticsDto lessIncidents= newWithInstancesAndIncidents(1351+831, 831);
     assertIsBefore(moreIncidents, lessIncidents);
  }
  
  @Test
  public void testComparatorZeroIncidents() {
     IncidentByProcessStatisticsDto moreInstances= newWithInstancesAndIncidents(172, 0);
     IncidentByProcessStatisticsDto lessInstances= newWithInstancesAndIncidents(114, 0);
     assertIsBefore(moreInstances, lessInstances);
  }
  
  @Test
  public void testComparatorSameIncidentsAndInstances() {
     IncidentByProcessStatisticsDto onlyOtherBPMN1= newWithInstancesAndIncidents(172, 0);
     onlyOtherBPMN1.setBpmnProcessId("1");
     IncidentByProcessStatisticsDto onlyOtherBPMN2= newWithInstancesAndIncidents(172, 0);
     onlyOtherBPMN2.setBpmnProcessId("2");
     assertIsBefore(onlyOtherBPMN1, onlyOtherBPMN2);
  }
  
  protected IncidentByProcessStatisticsDto newWithInstancesAndIncidents(int instances,int incidents) {
    IncidentByProcessStatisticsDto newObject = new IncidentByProcessStatisticsDto();
    newObject.setActiveInstancesCount(Long.valueOf(instances));
    newObject.setInstancesWithActiveIncidentsCount(incidents);
    return newObject;
  }
  
  protected void assertIsBefore(IncidentByProcessStatisticsDto first,IncidentByProcessStatisticsDto second) {
    assertThat(IncidentByProcessStatisticsDto.COMPARATOR.compare(first, second)).isLessThan(0);
  }

}
