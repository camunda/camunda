/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.camunda.operate.util.CollectionUtil;
import org.junit.Before;
import org.junit.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class PartitionHolderTest {
  
  Optional<Set<Integer>> zeebePartitionIds, elasticSearchPartitionIds;
  int sleeped = 0;

  // Mock the involved components
  PartitionHolder partitionHolder = new PartitionHolder() {
    @Override
    protected Optional<Set<Integer>> getPartitionIdsFromZeebe() {
      return zeebePartitionIds;
    }
    
    @Override
    protected Optional<Set<Integer>> getPartitionsFromElasticsearch() {
      return elasticSearchPartitionIds;
    }
    
    @Override
    protected void sleepFor(long milliseconds) {
      sleeped++;
    }
  };
  
  @Before
  public void setUp() {
    sleeped = 0;
    zeebePartitionIds = null;
    elasticSearchPartitionIds = null;
  }
  
  @Test
  public void testGetEmptyPartitionIdsWhenNoComponentAvailable() {
    zeebePartitionIds = Optional.empty();
    elasticSearchPartitionIds = Optional.empty();
    
    assertThat(partitionHolder.getPartitionIds()).isEmpty();
    assertThat(sleeped).isEqualTo(PartitionHolder.MAX_RETRY);
  }
  
  @Test
  public void testGetZeebeClientPartitionIds() {
    zeebePartitionIds = Optional.of(new HashSet<Integer>(CollectionUtil.fromTo(5, 10)));
    elasticSearchPartitionIds = Optional.empty();
    
    assertThat(partitionHolder.getPartitionIds()).isNotEmpty().contains(5,6,7,8,9,10);
    assertThat(sleeped).isEqualTo(0);
  }
  
  @Test
  public void testGetElasticsearchPartitionIds() {
    zeebePartitionIds = Optional.empty();
    elasticSearchPartitionIds = Optional.of(new HashSet<Integer>(CollectionUtil.fromTo(1, 5)));
    
    assertThat(partitionHolder.getPartitionIds()).isNotEmpty().contains(1,2,3,4,5);
    assertThat(sleeped).isEqualTo(0);
    // But then zeebePartitions are there
    zeebePartitionIds = Optional.of(new HashSet<Integer>(CollectionUtil.fromTo(2, 4)));
    assertThat(partitionHolder.getPartitionIds()).containsAll(zeebePartitionIds.get());
    assertThat(sleeped).isEqualTo(0);
  }
  
  @Test
  public void testGetPartitionIds() {
    zeebePartitionIds = Optional.of(new HashSet<Integer>(CollectionUtil.fromTo(1, 5)));
    elasticSearchPartitionIds = Optional.of(new HashSet<Integer>(CollectionUtil.fromTo(1, 5)));
    
    assertThat(partitionHolder.getPartitionIds()).isNotEmpty().contains(1,2,3,4,5);
    assertThat(sleeped).isEqualTo(0);
  }
  
  @Test
  public void testGetPartitionIdsWithDifferentSets() {
    zeebePartitionIds = Optional.of(new HashSet<Integer>(CollectionUtil.fromTo(1, 7)));
    elasticSearchPartitionIds = Optional.of(new HashSet<Integer>(CollectionUtil.fromTo(1, 5)));
    
    assertThat(partitionHolder.getPartitionIds()).containsAll(zeebePartitionIds.get());
    assertThat(sleeped).isEqualTo(0);
  }

}
