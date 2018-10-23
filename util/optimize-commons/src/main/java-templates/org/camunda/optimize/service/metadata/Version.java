package org.camunda.optimize.service.metadata;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class Version {
  private static final String RAW_VERSION = "${project.version}";
  // cleaned from potential clutter added by maven, e.g. -SNAPSHOT
  public static final String VERSION = Arrays.stream(RAW_VERSION.split("[^0-9]"))
    .filter(part -> part.chars().allMatch(Character::isDigit))
    .collect(Collectors.joining("."));
  public static final String VERSION_MAJOR = VERSION.split("\\.")[0];
  public static final String VERSION_MINOR = VERSION.split("\\.")[1];
  public static final String VERSION_PATCH = VERSION.split("\\.")[2];
}
