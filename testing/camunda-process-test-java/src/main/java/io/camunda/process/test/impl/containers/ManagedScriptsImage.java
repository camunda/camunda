/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.process.test.impl.containers;

import com.github.dockerjava.api.model.ContainerConfig;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.images.RemoteDockerImage;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.ImageNameSubstitutor;
import org.testcontainers.utility.LazyFuture;

/**
 * The Connectors image with the Python and Node.js interpreters that the {@code local} provider of
 * managed scripts runs scripts with. The image is built from the configured Connectors image when
 * the runtime starts, and kept, so that Docker reuses it in the next runs.
 *
 * <p>The Connectors image can be a custom image. If it has python3 and node already, it is used as
 * it is. Otherwise, the interpreters are installed with apk, as in the Connectors image. The built
 * image runs as the user of the Connectors image.
 */
public final class ManagedScriptsImage {

  public static final String ENV_MANAGED_CODE_ENABLED = "CAMUNDA_CONNECTOR_MANAGEDCODE_ENABLED";
  public static final String ENV_MANAGED_CODE_PROVIDER = "CAMUNDA_CONNECTOR_MANAGEDCODE_PROVIDER";
  public static final String ENV_MANAGED_CODE_LOCAL_DIRECTORY =
      "CAMUNDA_CONNECTOR_MANAGEDCODE_LOCAL_DIRECTORY";

  static final String IMAGE_NAME = "camunda-process-test/connectors-managed-scripts";

  // The user of the Connectors image has no home directory: the default directory of the local
  // provider, in the user's home, cannot be created.
  private static final String LOCAL_DIRECTORY = "/tmp/managed-script";

  // The minimum versions of the local provider.
  private static final int MIN_PYTHON_MINOR_VERSION = 10;
  private static final int MIN_NODE_MAJOR_VERSION = 20;

  // The first Connectors version with managed scripts.
  private static final int MIN_CONNECTORS_MAJOR_VERSION = 8;
  private static final int MIN_CONNECTORS_MINOR_VERSION = 11;

  private static final Pattern CONNECTORS_VERSION = Pattern.compile("(\\d+)\\.(\\d+)(?:[.-]|$)");
  private static final Pattern PYTHON_VERSION = Pattern.compile("3\\.(\\d+)");
  private static final Pattern NODE_VERSION = Pattern.compile("(\\d+)");

  private final String baseImage;
  private final String pythonPackage;
  private final String nodePackage;
  private final boolean hasPinnedVersion;

  /**
   * @param baseImage the Connectors image to add the interpreters to
   * @param pythonVersion the Python version, such as {@code 3.13}, or {@code null} for the latest
   *     one of the package repository of the image
   * @param nodeVersion the Node.js major version, such as {@code 22}, or {@code null} for the
   *     latest one of the package repository of the image
   */
  public ManagedScriptsImage(
      final String baseImage, final String pythonVersion, final String nodeVersion) {
    this.baseImage = baseImage;
    pythonPackage = pythonPackage(pythonVersion);
    nodePackage = nodePackage(nodeVersion);
    hasPinnedVersion = !isBlank(pythonVersion) || !isBlank(nodeVersion);
  }

  /** The environment that enables managed scripts with the local provider. */
  public static Map<String, String> envVars() {
    final Map<String, String> envVars = new HashMap<>();
    envVars.put(ENV_MANAGED_CODE_ENABLED, "true");
    envVars.put(ENV_MANAGED_CODE_PROVIDER, "local");
    envVars.put(ENV_MANAGED_CODE_LOCAL_DIRECTORY, LOCAL_DIRECTORY);
    return envVars;
  }

  /**
   * @param baseImageUser the user of the Connectors image, which the built image runs as too, or an
   *     empty string for root
   */
  public String dockerfile(final String baseImageUser) {
    return dockerfile(baseImage, baseImageUser);
  }

  /**
   * @param fromImage the resolved Connectors image, which is the configured one unless an image
   *     name substitutor of Testcontainers, such as a registry mirror, changes it
   */
  String dockerfile(final String fromImage, final String baseImageUser) {
    // apk needs root. The Connectors image runs as 1001, but a custom image can use another user.
    final StringBuilder dockerfile =
        new StringBuilder().append("FROM ").append(fromImage).append("\n").append("USER 0\n");
    dockerfile.append("RUN ");
    if (!hasPinnedVersion) {
      // A custom image that has the interpreters already is used as it is, whatever its base.
      dockerfile.append(
          "if command -v python3 >/dev/null && command -v node >/dev/null; then :; \\\n  el");
    }
    dockerfile
        .append("if command -v apk >/dev/null; then apk add --no-cache ")
        .append(pythonPackage)
        .append(" ")
        .append(nodePackage)
        .append("; \\\n  else echo '")
        .append(missingInterpretersMessage())
        .append("' >&2; exit 1; fi\n");
    if (!isBlank(baseImageUser)) {
      dockerfile.append("USER ").append(baseImageUser.trim()).append("\n");
    }
    return dockerfile.toString();
  }

  /** The name of the built image, which changes with its Dockerfile only. */
  public String imageName(final String baseImageUser) {
    return imageName(baseImage, baseImageUser);
  }

  String imageName(final String fromImage, final String baseImageUser) {
    return IMAGE_NAME + ":" + sha256(dockerfile(fromImage, baseImageUser)).substring(0, 12);
  }

  /**
   * Resolves the Connectors image, then builds the image when the container starts.
   *
   * @param pullBaseImage whether to pull the Connectors image, for a mutable tag
   */
  public RemoteDockerImage build(final boolean pullBaseImage) {
    // The built image exists locally only: a registry mirror or a pull policy must not apply to it.
    // They apply to the Connectors image, when it is resolved.
    return new RemoteDockerImage(buildLazily(pullBaseImage))
        .withImageNameSubstitutor(ImageNameSubstitutor.noop())
        .withImagePullPolicy(imageName -> false);
  }

  private Future<String> buildLazily(final boolean pullBaseImage) {
    return new LazyFuture<String>() {
      @Override
      protected String resolve() {
        final String resolvedBaseImage =
            new RemoteDockerImage(DockerImageName.parse(baseImage))
                .withImagePullPolicy(
                    pullBaseImage ? PullPolicy.alwaysPull() : PullPolicy.defaultPolicy())
                .get();
        final ContainerConfig config =
            DockerClientFactory.lazyClient().inspectImageCmd(resolvedBaseImage).exec().getConfig();
        final String user = config == null ? null : config.getUser();
        return build(resolvedBaseImage, user).get();
      }
    };
  }

  ImageFromDockerfile build(final String fromImage, final String baseImageUser) {
    // Not deleted on exit: the image and its layers are reused by the next runs.
    return new ImageFromDockerfile(imageName(fromImage, baseImageUser), false)
        .withFileFromString("Dockerfile", dockerfile(fromImage, baseImageUser));
  }

  private String missingInterpretersMessage() {
    final String interpreters =
        hasPinnedVersion ? "the Python and Node.js versions" : "python3 and node";
    return "Managed scripts: the Connectors image "
        + baseImage
        + " has no apk to install "
        + interpreters
        + ". Base it on camunda/connectors-bundle, or install "
        + interpreters
        + " in it.";
  }

  /**
   * Rejects a Connectors version without managed scripts, instead of a test that waits for script
   * jobs nobody handles. A version that is not a release version, such as {@code latest} or a
   * custom tag, is accepted.
   */
  public static void checkConnectorsVersion(final String connectorsVersion) {
    if (connectorsVersion == null) {
      return;
    }
    final Matcher matcher = CONNECTORS_VERSION.matcher(connectorsVersion.trim());
    if (!matcher.lookingAt()) {
      return;
    }
    final int major = Integer.parseInt(matcher.group(1));
    final int minor = Integer.parseInt(matcher.group(2));
    if (major < MIN_CONNECTORS_MAJOR_VERSION
        || (major == MIN_CONNECTORS_MAJOR_VERSION && minor < MIN_CONNECTORS_MINOR_VERSION)) {
      throw new IllegalArgumentException(
          String.format(
              "Managed scripts need Connectors %d.%d or later, but the Connectors Docker image"
                  + " version is '%s'. Set a newer version with"
                  + " withConnectorsDockerImageVersion().",
              MIN_CONNECTORS_MAJOR_VERSION, MIN_CONNECTORS_MINOR_VERSION, connectorsVersion));
    }
  }

  private static String pythonPackage(final String version) {
    if (isBlank(version)) {
      return "python3";
    }
    final Matcher matcher = PYTHON_VERSION.matcher(version.trim());
    if (!matcher.matches() || Integer.parseInt(matcher.group(1)) < MIN_PYTHON_MINOR_VERSION) {
      throw new IllegalArgumentException(
          String.format(
              "Invalid Python version '%s' for managed scripts: expected 3.%d or later, such as"
                  + " '3.13'",
              version, MIN_PYTHON_MINOR_VERSION));
    }
    return "python-" + version.trim();
  }

  private static String nodePackage(final String version) {
    if (isBlank(version)) {
      return "nodejs";
    }
    final Matcher matcher = NODE_VERSION.matcher(version.trim());
    if (!matcher.matches() || Integer.parseInt(matcher.group(1)) < MIN_NODE_MAJOR_VERSION) {
      throw new IllegalArgumentException(
          String.format(
              "Invalid Node.js version '%s' for managed scripts: expected a major version of %d or"
                  + " later, such as '22'",
              version, MIN_NODE_MAJOR_VERSION));
    }
    return "nodejs-" + version.trim();
  }

  private static boolean isBlank(final String value) {
    return value == null || value.trim().isEmpty();
  }

  private static String sha256(final String value) {
    try {
      final byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      final StringBuilder hex = new StringBuilder();
      for (final byte b : digest) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
