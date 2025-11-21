/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.dynamic.nodeid.Lease.VersionMappings;
import io.camunda.zeebe.dynamic.nodeid.repository.Metadata;
import io.camunda.zeebe.dynamic.nodeid.repository.NodeIdRepository.StoredLease;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class StoredLeaseTest {

  private final NodeInstance nodeInstance = new NodeInstance(2, Version.of(2L));
  private final ControlledInstantSource clock = new ControlledInstantSource(Instant.now());
  private final String taskId = "newTaskId";
  private final Duration expiryDuration = Duration.ofSeconds(15);

  @Nested
  class Uninitialized{
    @Test
    void shouldAlwaysContainNodeId(){
      assertThatThrownBy(() -> new StoredLease.Uninitialized(null, "asdasd")).hasMessageContaining("node cannot be null");
    }

    @Test
    void shouldAlwaysContainETag(){
      assertThatThrownBy(() -> new StoredLease.Uninitialized(new NodeInstance(2, Version.of(2L)), "")).hasMessageContaining("eTag cannot be empty");
      assertThatThrownBy(() -> new StoredLease.Uninitialized(new NodeInstance(2, Version.of(2L)), null))
          .hasMessageContaining("eTag cannot be null");
    }

    @Test
    void canBeAcquired(){
      //given
      var stored = new StoredLease.Uninitialized(nodeInstance, "eTagExample");

      //when
      var toAcquire = stored.acquireInitialLease(taskId, clock, expiryDuration);

      // then
      assertThat(toAcquire)
          .returns(nodeInstance.nextVersion(), Lease::nodeInstance)
          .returns(taskId, Lease::taskId)
          .returns(VersionMappings.of(nodeInstance.nextVersion()), Lease::versionMappings)
          .returns(expiryFromNow(), Lease::timestamp);
    }
  }


  @Nested
  class Initialized{

    @Test
    void shouldAlwaysContainMetadata(){
      assertThatThrownBy(() -> new StoredLease.Initialized(null,0, "asd")).hasMessageContaining("metadata cannot be null");
      assertThatThrownBy(
              () ->
                  new StoredLease.Initialized(
                      null, new Lease("asdasd", 123, new NodeInstance(0, Version.of(1)), VersionMappings.empty()), "asd"))
          .hasMessageContaining("metadata cannot be null");
    }

    @Test
    void shouldAlwaysContainETag(){
      assertThatThrownBy(() -> new StoredLease.Initialized(new Metadata("asd", 123, Version.of(1)), 0, null))
          .hasMessageContaining("eTag cannot be null");
    }
    @Test
    void shouldNotAcquireAValidLease(){
    //given
    var stored = new StoredLease.Initialized(new Metadata(taskId, expiryFromNow(), nodeInstance.version()), nodeInstance.id(), "eTagExample");

      var newTaskId = "newTaskId";
      var acquired = stored.acquireInitialLease(newTaskId, clock, expiryDuration);

      //then
      assertThat(acquired).isNull();
    }
    @Test
    void shouldAcquireWithANewVersionWhenExpired(){
      //given
      var stored = new StoredLease.Initialized(new Metadata(taskId, expiryFromNow(), nodeInstance.version()), nodeInstance.id(), "eTagExample");

      //when
      clock.advance(expiryDuration.plusMillis(1));
      var newTaskId = "newTaskId";
      var acquired = stored.acquireInitialLease(newTaskId, clock, expiryDuration);

      //then
      assertThat(acquired)
          .returns(nodeInstance.nextVersion(), Lease::nodeInstance)
          .returns(newTaskId, Lease::taskId)
          .returns(VersionMappings.of(nodeInstance.nextVersion()), Lease::versionMappings)
          .returns(expiryFromNow(), Lease::timestamp);

    }
  }

  long expiryFromNow(){return clock.millis() + expiryDuration.toMillis();}
}
