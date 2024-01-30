/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.migration;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.util.StringUtils;

/**
 * SemanticVersion - https://semver.org
 * 
 * This class is slightly adapted from 
 * <a href="https://github.com/senacor/elasticsearch-evolution/blob/master/elasticsearch-evolution-core/src/main/java/com/senacor/elasticsearch/evolution/core/internal/model/MigrationVersion.java">elasticssearch-evolution</a>
 */
public final class SemanticVersion implements Comparable<SemanticVersion> {
    /**
     * Compiled pattern for matching proper version format
     */
    private static Pattern splitPattern = Pattern.compile("\\.(?=\\d)");

    /**
     * The individual parts this version string is composed of. Ex. 1.2.3.4.0 -> [1, 2, 3, 4, 0]
     */
    private final List<Integer> versionParts;

    /**
     * The printable text to represent the version.
     */
    private final String displayText;

    /**
     * Factory for creating a SemanticVersion from a version String
     *
     * @param version The version String like
     * @return The MigrationVersion
     */
    public static SemanticVersion fromVersion(final String version) {
        return new SemanticVersion(version);
    }

    public boolean isBetween(final SemanticVersion olderVersion,final SemanticVersion newerVersion) {
      return isNewerThan(olderVersion) && !isNewerThan(newerVersion);
    }
    
    /**
     * Creates a Version using this version string.
     *
     * @param version The version in one of the following formats: 6, 6.0, 005, 1.2.3.4, 201004200021.
     */
    public SemanticVersion(final String version) {
        if(StringUtils.isEmpty(version)) {
          throw new IllegalArgumentException("version should not be null or empty");
        }
        if(StringUtils.containsWhitespace(version)) {
          throw new IllegalArgumentException("version should not contain white space");
        }
        String normalizedVersion = version.replace('_', '.');
        if (normalizedVersion.toLowerCase().indexOf("-") > 0) {       
          normalizedVersion = normalizedVersion.substring(0, normalizedVersion.toLowerCase().indexOf("-"));
        }
        this.versionParts = tokenize(normalizedVersion);
        this.displayText = versionParts.stream()
                .map(Object::toString)
                .collect(Collectors.joining("."));
    }

    /**
     * @return The textual representation of the version.
     */
    @Override
    public String toString() {
        return displayText;
    }

    /**
     * @return Numeric version as String
     */
    public String getVersion() {
        return displayText;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SemanticVersion version1 = (SemanticVersion) o;

        return compareTo(version1) == 0;
    }

    @Override
    public int hashCode() {
        return versionParts == null ? 0 : versionParts.hashCode();
    }

    /**
     * Convenience method for quickly checking whether this version is at least as new as this other version.
     *
     * @param otherVersion The other version.
     * @return {@code true} if this version is equal or newer, {@code false} if it is older.
     */
    public boolean isAtLeast(String otherVersion) {
        return compareTo(SemanticVersion.fromVersion(otherVersion)) >= 0;
    }

    /**
     * Convenience method for quickly checking whether this version is newer than this other version.
     *
     * @param otherVersion The other version.
     * @return {@code true} if this version is newer, {@code false} if it is not.
     */
    public boolean isNewerThan(String otherVersion) {
        return compareTo(SemanticVersion.fromVersion(otherVersion)) > 0;
    }
    
    public boolean isNewerThan(SemanticVersion otherVersion) {
      return compareTo(otherVersion) > 0;
    }

    /**
     * Convenience method for quickly checking whether this major version is newer than this other major version.
     *
     * @param otherVersion The other version.
     * @return {@code true} if this major version is newer, {@code false} if it is not.
     */
    public boolean isMajorNewerThan(String otherVersion) {
        return isMajorNewerThan(SemanticVersion.fromVersion(otherVersion));
    }

    /**
     * Convenience method for quickly checking whether this major version is newer than this other major version.
     *
     * @param otherVersion The other version.
     * @return {@code true} if this major version is newer, {@code false} if it is not.
     */
    public boolean isMajorNewerThan(SemanticVersion otherVersion) {
        return getMajor().compareTo(otherVersion.getMajor()) > 0;
    }

    /**
     * @return The major version.
     */
    public Integer getMajor() {
        return versionParts.get(0);
    }

    /**
     * @return The major version as a string.
     */
    public String getMajorAsString() {
        return versionParts.get(0).toString();
    }

    public Integer getMinor() {
      if (versionParts.size() == 1) {
        return 0;
      }
      return versionParts.get(1);
    }
    
    /**
     * @return The minor version as a string.
     */
    public String getMinorAsString() {
        if (versionParts.size() == 1) {
            return "0";
        }
        return versionParts.get(1).toString();
    }
    
    @Override
    public int compareTo(SemanticVersion o) {
        if (o == null) {
            return 1;
        }

        final List<Integer> parts1 = versionParts;
        final List<Integer> parts2 = o.versionParts;
        int largestNumberOfParts = Math.max(parts1.size(), parts2.size());
        for (int i = 0; i < largestNumberOfParts; i++) {
            final int compared = getOrZero(parts1, i).compareTo(getOrZero(parts2, i));
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    }

    private Integer getOrZero(final List<Integer> elements, int i) {
        return i < elements.size() ? elements.get(i) : 0;
    }

    /**
     * Splits this string into list of Long
     *
     * @param str The string to split.
     * @return The resulting array.
     */
    private List<Integer> tokenize(final String version) {
        List<Integer> parts = new ArrayList<>();
        try {
            for (String part : splitPattern.split(version)) {
                parts.add(Integer.valueOf(part));
            }
        } catch (NumberFormatException e) {
            throw new RuntimeException(
                    "Invalid version containing non-numeric characters. Only 0..9 and . are allowed. Invalid version: "
                            + version);
        }
        for (int i = parts.size() - 1; i > 0; i--) {
            if (!parts.get(i).equals(0)) {
                break;
            }
            parts.remove(i);
        }
        return parts;
    }
}
