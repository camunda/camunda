package org.camunda.optimize.service.metadata;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class Version {
  public static final String RAW_VERSION = "${project.version}";
  public static final String VERSION = stripToPlainVersion(RAW_VERSION);
  public static final String VERSION_MAJOR = VERSION.split("\\.")[0];
  public static final String VERSION_MINOR = VERSION.split("\\.")[1];
  public static final String VERSION_PATCH = VERSION.split("\\.")[2];

  public static final String stripToPlainVersion(final String rawVersion) {
    // extract plain <major>.<minor>.<patch> version, strip everything else
    return Arrays.stream(rawVersion.split("[^0-9]"))
      .limit(3)
      .filter(part -> part.chars().allMatch(Character::isDigit))
      .collect(Collectors.joining("."));
  }
}
