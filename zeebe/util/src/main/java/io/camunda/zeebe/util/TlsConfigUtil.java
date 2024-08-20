/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import java.io.File;

public class TlsConfigUtil {
  public static void validateTlsConfig(
      final File certificateChain, final File privateKey, final File keyStore) {
    if ((certificateChain != null || privateKey != null) && keyStore != null) {
      throw new IllegalArgumentException(
          String.format(
              """
                      Cannot provide both separate certificate chain and or private key along with a
                      keystore file, use only one approach.
                      certificateChainPath: %s
                      privateKeyPath: %s
                      OR
                      keyStorePath: %s""",
              certificateChain, privateKey, keyStore));
    }

    if (keyStore == null) {
      if (certificateChain == null) {
        throw new IllegalArgumentException(
            "Expected to have a valid certificate chain path for network security, but none "
                + "configured");
      }

      if (privateKey == null) {
        throw new IllegalArgumentException(
            "Expected to have a valid private key path for network security, but none configured");
      }

      if (!certificateChain.canRead()) {
        throw new IllegalArgumentException(
            String.format(
                "Expected the configured network security certificate chain path '%s' to point to a"
                    + " readable file, but it does not",
                certificateChain));
      }

      if (!privateKey.canRead()) {
        throw new IllegalArgumentException(
            String.format(
                "Expected the configured network security private key path '%s' to point to a "
                    + "readable file, but it does not",
                privateKey));
      }
    } else {
      if (!keyStore.canRead()) {
        throw new IllegalArgumentException(
            String.format(
                "Expected the configured network security keystore file '%s' to point to a "
                    + "readable file, but it does not",
                keyStore));
      }
    }
  }
}
