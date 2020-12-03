/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util.socket;

import io.zeebe.test.util.TestEnvironment;
import java.net.InetSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SocketUtil {
  static final Logger LOG = LoggerFactory.getLogger("io.zeebe.test.util.SocketUtil");

  private static final String DEFAULT_HOST = "localhost";
  // Docker maps exposed ports to random local ports, starting at 32768; this is undocumented, but
  // observed empirically, and is aligned with Linux conventions, e.g. see
  // https://tldp.org/LDP/solrhe/Securing-Optimizing-Linux-RH-Edition-v1.3/chap6sec70.html
  private static final int DOCKER_BASE_PORT = 32768;
  // most unix based systems reserve ports from 0-1024; picking 1025 as our base port gives us
  // plenty of ports before we will collide with Docker's base port
  private static final int BASE_PORT = 1025;
  // defines the upper bound for how many separate maven processes (not forks or maven parallel
  // build count) can be ran in parallel on the same machine, which is typically defined in the CI
  // pipeline (e.g. Jenkinsfile)
  private static final int MAX_TEST_STAGES = 10;
  // defines the upper bound for how many forks can be ran per stage; this should be the number of
  // maven threads (-T option) times the configured surefire/failsafe forkCount (see the surefire
  // or failsafe config in the pom)
  private static final int MAX_TEST_FORKS_PER_STAGE = 30;
  private static final int PORT_RANGE_PER_TEST_FORK = 100;
  private static final int PORT_RANGE_PER_TEST_STAGE =
      PORT_RANGE_PER_TEST_FORK * MAX_TEST_FORKS_PER_STAGE;

  private static final int TEST_FORK_NUMBER;
  private static final PortRange PORT_RANGE;

  static {
    final int testForkNumber = TestEnvironment.getTestForkNumber();
    // test stage in Jenkins (junit8, junit, it)
    final int testMavenId = TestEnvironment.getTestMavenId();

    LOG.info(
        "Starting socket assignment with testForkNumber {} and testMavenId {}",
        testForkNumber,
        testMavenId);

    // ensure limits to stay in available port range
    assert testForkNumber < MAX_TEST_FORKS_PER_STAGE
        : "System property test fork number has to be smaller than " + MAX_TEST_FORKS_PER_STAGE;
    assert testMavenId < MAX_TEST_STAGES
        : "System property test maven id has to be smaller than " + MAX_TEST_STAGES;
    final int absoluteMaxPort = BASE_PORT + MAX_TEST_STAGES * PORT_RANGE_PER_TEST_STAGE;
    // this assert seems unnecessary but is there to prevent changes to the constants above from
    // causing potential collisions with the base port that the Docker daemon uses
    assert DOCKER_BASE_PORT > absoluteMaxPort
        : "Maximum port "
            + absoluteMaxPort
            + " across all forks should be less than "
            + DOCKER_BASE_PORT
            + ", the minimum port of Docker";

    final int testOffset =
        testMavenId * PORT_RANGE_PER_TEST_STAGE + testForkNumber * PORT_RANGE_PER_TEST_FORK;
    final int min = BASE_PORT + testOffset;
    final int max = min + PORT_RANGE_PER_TEST_FORK;

    TEST_FORK_NUMBER = testForkNumber;
    PORT_RANGE = new PortRange(DEFAULT_HOST, TEST_FORK_NUMBER, min, max);
  }

  private SocketUtil() {}

  public static synchronized InetSocketAddress getNextAddress() {
    return PORT_RANGE.next();
  }
}
