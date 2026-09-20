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
package io.camunda.runner.internal;

import java.security.SecureRandom;

/** Generates {@code <username>-<5 char base36>} run ids. Username falls back to {@code user}. */
public final class RunIdGenerator {

  private static final SecureRandom RNG = new SecureRandom();
  private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz";

  private RunIdGenerator() {}

  /** Returns a fresh run id like {@code stephan-r7f3a}. */
  public static String generate() {
    return generate(systemUser());
  }

  /** Test-friendly seam. */
  public static String generate(final String user) {
    final String sanitized = sanitize(user);
    final StringBuilder suffix = new StringBuilder(5);
    for (int i = 0; i < 5; i++) {
      suffix.append(ALPHABET.charAt(RNG.nextInt(ALPHABET.length())));
    }
    return sanitized + "-" + suffix;
  }

  private static String systemUser() {
    final String name = System.getProperty("user.name");
    return name == null || name.isBlank() ? "user" : name;
  }

  private static String sanitize(final String user) {
    final StringBuilder b = new StringBuilder(user.length());
    for (final char c : user.toCharArray()) {
      if (Character.isLetterOrDigit(c) || c == '_') {
        b.append(c);
      }
    }
    return b.length() == 0 ? "user" : b.toString();
  }
}
