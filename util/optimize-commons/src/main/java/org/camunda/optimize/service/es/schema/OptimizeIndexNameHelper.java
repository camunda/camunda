package org.camunda.optimize.service.es.schema;

import org.camunda.optimize.service.metadata.Version;

public class OptimizeIndexNameHelper {
  private static final String OPTIMIZE_INDEX_PREFIX = "optimize-";

  private OptimizeIndexNameHelper() {
  }

  public static String getOptimizeIndexAliasForType(String type) {
    String original = OPTIMIZE_INDEX_PREFIX + type;
    return original.toLowerCase();
  }

  public static String getCurrentVersionOptimizeIndexNameForAlias(final String indexAlias) {
    return getOptimizeIndexNameForAliasAndVersion(indexAlias, Version.VERSION);
  }

  public static String getOptimizeIndexNameForAliasAndVersion(final String indexAlias, final String version) {
    final String versionSuffix = version != null
      ? "_v" + Version.getMajorVersionFrom(version) + "." + Version.getMinorVersionFrom(version)
      : "";
    return indexAlias + versionSuffix;
  }
}