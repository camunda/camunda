package io.atomix.core;

import io.atomix.cluster.Node;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.discovery.MulticastDiscoveryProvider;
import io.atomix.core.profile.Profile;
import io.atomix.utils.net.Address;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.rules.ExternalResource;
import org.slf4j.LoggerFactory;

public final class AtomixRule extends ExternalResource {

  private static final int BASE_PORT = 5000;
  private static final File DATA_DIR = new File(System.getProperty("user.dir"), ".data");

  @Override
  protected void before() throws Throwable {
    deleteData();
  }

  @Override
  protected void after() {
    try {
      deleteData();
    } catch (final Exception e) {
      LoggerFactory.getLogger(AtomixRule.class).error("Unexpected error on clean up data", e);
    }
  }

  /** Creates an Atomix instance. */
  public AtomixBuilder buildAtomix(final int id, final Properties properties) {
    return Atomix.builder()
        .withClusterId("test")
        .withMemberId(String.valueOf(id))
        .withHost("localhost")
        .withPort(BASE_PORT + id)
        .withProperties(properties)
        .withMulticastEnabled()
        .withMembershipProvider(new MulticastDiscoveryProvider());
  }

  /** Creates an Atomix instance. */
  public AtomixBuilder buildAtomix(
      final int id, final List<Integer> memberIds, final Properties properties) {
    final Collection<Node> nodes =
        memberIds.stream()
            .map(
                memberId ->
                    Node.builder()
                        .withId(String.valueOf(memberId))
                        .withAddress(Address.from("localhost", BASE_PORT + memberId))
                        .build())
            .collect(Collectors.toList());

    return Atomix.builder()
        .withClusterId("test")
        .withMemberId(String.valueOf(id))
        .withHost("localhost")
        .withPort(BASE_PORT + id)
        .withProperties(properties)
        .withMulticastEnabled()
        .withMembershipProvider(
            !nodes.isEmpty()
                ? new BootstrapDiscoveryProvider(nodes)
                : new MulticastDiscoveryProvider());
  }

  /** Creates an Atomix instance. */
  public Atomix createAtomix(
      final int id, final List<Integer> bootstrapIds, final Profile... profiles) {
    return createAtomix(id, bootstrapIds, new Properties(), profiles);
  }

  /** Creates an Atomix instance. */
  public Atomix createAtomix(
      final int id,
      final List<Integer> bootstrapIds,
      final Properties properties,
      final Profile... profiles) {
    return createAtomix(id, bootstrapIds, properties, b -> b.withProfiles(profiles).build());
  }

  /** Creates an Atomix instance. */
  public Atomix createAtomix(
      final int id,
      final List<Integer> bootstrapIds,
      final Function<AtomixBuilder, Atomix> builderFunction) {
    return createAtomix(id, bootstrapIds, new Properties(), builderFunction);
  }

  /** Creates an Atomix instance. */
  public Atomix createAtomix(
      final int id,
      final List<Integer> bootstrapIds,
      final Properties properties,
      final Function<AtomixBuilder, Atomix> builderFunction) {
    return builderFunction.apply(buildAtomix(id, bootstrapIds, properties));
  }

  private int findAvailablePort(final int defaultPort) {
    try {
      final ServerSocket socket = new ServerSocket(0);
      socket.setReuseAddress(true);
      final int port = socket.getLocalPort();
      socket.close();
      return port;
    } catch (final IOException ex) {
      return defaultPort;
    }
  }

  /** Deletes data from the test data directory. */
  private static void deleteData() throws Exception {
    final Path directory = DATA_DIR.toPath();
    if (Files.exists(directory)) {
      Files.walkFileTree(
          directory,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
                throws IOException {
              Files.delete(file);
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path dir, final IOException exc)
                throws IOException {
              Files.delete(dir);
              return FileVisitResult.CONTINUE;
            }
          });
    }
  }
}
