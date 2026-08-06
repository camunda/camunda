/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.testcontainers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.utility.DockerImageName;

/**
 * Minio is a S3 compatible high performance object storage. See <a href="https://min.io/">their
 * official page</a> for more.
 *
 * <p>We use it primarily to test compatibility with our S3 backup instance, as it's lightweight and
 * popular.
 *
 * <p>When using this, keep in mind that you will need to add a network alias per bucket that you
 * want to create. You should use {@link #withDomain(String, String...)} for this, and read the
 * method's documentation.
 *
 * <p>In most cases, it's expected you will configure the domain as above.
 */
public final class MinioContainer extends GenericContainer<MinioContainer> {

  private static final DockerImageName IMAGE = DockerImageName.parse("minio/minio");
  private static final int PORT = 9000;
  private static final String DEFAULT_REGION = "us-east-1";
  private static final String DEFAULT_ACCESS_KEY = "accessKey";
  private static final String DEFAULT_SECRET_KEY = "secretKey";

  /** Name the {@code mc} client uses inside the container for this Minio instance. */
  private static final String ALIAS = "local";

  private String domain;

  /**
   * Creates a default container with a pinned image version. It's unlikely we ever need to change
   * this.
   */
  public MinioContainer() {
    this("RELEASE.2025-04-22T22-12-26Z");
  }

  /**
   * Creates a new container with the specific image version.
   *
   * @param version the minio version to use
   */
  @SuppressWarnings("resource")
  public MinioContainer(final String version) {
    super(IMAGE.withTag(version));

    withCommand("server /data")
        .withExposedPorts(PORT)
        .withEnv("MINIO_ROOT_USER", DEFAULT_ACCESS_KEY)
        .withEnv("MINIO_ROOT_PASSWORD", DEFAULT_SECRET_KEY)
        .withEnv("MINIO_REGION", DEFAULT_REGION)
        .waitingFor(defaultWaitStrategy());
  }

  public WaitStrategy defaultWaitStrategy() {
    return new HttpWaitStrategy()
        .forPath("/minio/health/live")
        .forPort(PORT)
        .withStartupTimeout(Duration.ofMinutes(1));
  }

  /**
   * Returns the S3 accessible endpoint for a client running on the host machine. Note that this may
   * use a hostname. If you wish to use path-style access (e.g. you don't know your buckets
   * beforehand), then you can format the endpoint yourself using 127.0.0.1 as the IP address
   * instead of the host.
   *
   * <p>NOTE: if this is a common use case, we can add a method here that does so.
   */
  public String externalEndpoint() {
    return "http://%s:%d".formatted(getHost(), getMappedPort(PORT));
  }

  /**
   * Returns the internal endpoint, i.e. the S3 accessible endpoint used when your client is running
   * in a container in the same network as this one. If you wish to use path-style access (e.g. you
   * don't know your buckets beforehand), then you can format the endpoint yourself using the
   * container's internal IP address. You can fetch that by inspecting the container via {@link
   * #getContainerInfo()} and checking the network settings for the IP address.
   *
   * <p>NOTE: if this is a common use case, we can add a method here that does so.
   */
  public String internalEndpoint() {
    return "http://%s:%d".formatted(internalHost(), PORT);
  }

  /** Returns the configured Minio region. You can pass this to your S3 client builder. */
  public String region() {
    return getEnvMap().getOrDefault("MINIO_REGION", DEFAULT_REGION);
  }

  /** Returns the configured Minio access key. You can pass this to your S3 client builder. */
  public String accessKey() {
    return getEnvMap().getOrDefault("MINIO_ACCESS_KEY", DEFAULT_ACCESS_KEY);
  }

  /** Returns the configured Minio secret key. You can pass this to your S3 client builder. */
  public String secretKey() {
    return getEnvMap().getOrDefault("MINIO_SECRET_KEY", DEFAULT_SECRET_KEY);
  }

  /**
   * Configures Minio to use the specific domain as its internal hostname and virtual wild card
   * host, allowing subdomain access for buckets. In order for the bucket subdomain to be
   * resolvable, you must provide them here, so they can be added as routes to the network.
   *
   * <p>So if you pass, say, {@code minio.local}, and two buckets called {@code bucketA} and {@code
   * bucketB}, you can access the following domains: {@code http://minio.local}, {@code
   * http://bucketA.minio.local}, and {@code http://bucketB.minio.local}. This is the default
   * operating mode for an S3 client.
   *
   * <p>If you do not know your bucket names in advance, then you will need to find to use the
   * container's IP address as the endpoint; this will force your client to use the path-style
   * access to access your buckets.
   *
   * @param domain the root domain accessible from the container's network
   * @param buckets a list of bucket names which will be added as subdomains for the root domain
   * @return this container for chaining
   */
  public MinioContainer withDomain(final String domain, final String... buckets) {
    this.domain = Objects.requireNonNull(domain, "must specify a domain");
    withEnv("MINIO_DOMAIN", domain).withNetworkAliases(domain);
    Arrays.stream(buckets)
        .map(name -> "%s.%s".formatted(name, domain))
        .forEach(this::withNetworkAliases);

    if (buckets.length == 0) {
      logger()
          .warn(
              "Configured minio with a domain but no buckets; make sure to use the container's IP"
                  + " address when accessing S3 via a client to enforce path-style access to your buckets");
    }

    return this;
  }

  /**
   * Creates a Minio user with a policy granting it full access to exactly the named buckets, and to
   * nothing else. The container must already be started.
   *
   * <p>Give each client under test its own user whenever the point of the test is that the client
   * authenticates as its own identity rather than as the process. Handing every client the root key
   * pair cannot show that: a client that silently falls back to the ambient credentials, or that
   * picks up a sibling's, still reaches the bucket and the test stays green. With a scoped user it
   * fails with {@code AccessDenied} instead.
   *
   * @param accessKey the user's access key, also used to name its policy
   * @param secretKey the user's secret key; Minio requires at least 8 characters
   * @param buckets the buckets the user may access — at least one
   */
  public void createScopedUser(
      final String accessKey, final String secretKey, final String... buckets) {
    if (buckets.length == 0) {
      throw new IllegalArgumentException(
          "must scope the user to at least one bucket, otherwise it can reach nothing");
    }

    final String policyName = accessKey + "-policy";
    final String policyFile = "/tmp/" + policyName + ".json";
    final String resources =
        Arrays.stream(buckets)
            .flatMap(bucket -> Stream.of(bucket, bucket + "/*"))
            .map("\"arn:aws:s3:::%s\""::formatted)
            .collect(Collectors.joining(","));
    final String policy =
        "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Action\":[\"s3:*\"],"
            + "\"Resource\":[%s]}]}".formatted(resources);

    mc("alias", "set", ALIAS, "http://localhost:" + PORT, accessKey(), secretKey());
    exec("sh", "-c", "printf '%s' '" + policy + "' > " + policyFile);
    mc("admin", "user", "add", ALIAS, accessKey, secretKey);
    mc("admin", "policy", "create", ALIAS, policyName, policyFile);
    mc("admin", "policy", "attach", ALIAS, policyName, "--user", accessKey);
  }

  private void mc(final String... arguments) {
    final String[] command = new String[arguments.length + 1];
    command[0] = "mc";
    System.arraycopy(arguments, 0, command, 1, arguments.length);
    exec(command);
  }

  private void exec(final String... command) {
    try {
      final var result = execInContainer(command);
      if (result.getExitCode() != 0) {
        throw new IllegalStateException(
            "Failed to run '%s' in the Minio container (exit code %d): %s%s"
                .formatted(
                    String.join(" ", command),
                    result.getExitCode(),
                    result.getStdout(),
                    result.getStderr()));
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private String internalHost() {
    if (domain != null) {
      return domain;
    }

    final var networkAliases = getNetworkAliases();
    if (networkAliases.isEmpty()) {
      return getContainerInfo().getName();
    }

    return networkAliases.get(networkAliases.size() - 1);
  }
}
