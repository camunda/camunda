/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.client.transport;

import org.opensearch.client.ApiClient;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

/**
 * This class represents a SemVer version, with an optional patch revision.
 */
public class Version {

    private final int major;
    private final int minor;
    private final int maintenance;
    private final boolean isPreRelease;

    /**
     * Parse a version string formatted using the standard Maven version format.
     *
     * @return the version, or {@code null} if the version could not be parsed.
     */
    public static Version parse(String version) {
        int hyphen = version.indexOf('-');
        boolean isPreRelease;
        if (hyphen >= 0) {
            version = version.substring(0, hyphen);
            isPreRelease = true;
        }
        else {
            isPreRelease = false;
        }

        String[] bits = version.split("\\.");
        try {
            int major = (bits.length >= 1) ? Integer.parseInt(bits[0]) : 0;
            int minor = (bits.length >= 2) ? Integer.parseInt(bits[1]) : 0;
            int maintenance = (bits.length >= 3) ? Integer.parseInt(bits[2]) : -1;
            return new Version(major, minor, maintenance, isPreRelease);
        }
        catch(NumberFormatException ex) {
            return null;
        }
    }

    public Version(int major, int minor, int maintenance, boolean isPreRelease) {
        this.major = major;
        this.minor = minor;
        this.maintenance = maintenance;
        this.isPreRelease = isPreRelease;
    }

    public int major() {
        return major;
    }

    public int minor() {
        return minor;
    }

    public int maintenance() {
        return maintenance;
    }

    public boolean isPreRelease() {
        return isPreRelease;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Version)) return false;
        Version that = (Version) other;
        return (major == that.major &&
                minor == that.minor &&
                maintenance == that.maintenance &&
                isPreRelease == that.isPreRelease);
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, maintenance, isPreRelease);
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append(major);
        s.append('.');
        s.append(minor);
        if (maintenance != -1) {
            s.append('.');
            s.append(maintenance);
        }
        if (isPreRelease) {
            s.append('p');
        }
        return s.toString();
    }

    /**
     * This library's version, read from the classpath. Can be {@code null} if the version resource could not be read.
     */
    @Nullable
    public static final Version VERSION;

    static {
        Version version = null;
        InputStream in = ApiClient.class.getResourceAsStream("version.properties");
        if (in != null) {
            Properties properties = new Properties();
            try {
                properties.load(in);
                String versionStr = properties.getProperty("version");
                if (versionStr != null) {
                    version = Version.parse(versionStr);
                }
            } catch (Exception e) {
                // Failed to read version.properties file
            }
        }
        VERSION = version;
    }
}
