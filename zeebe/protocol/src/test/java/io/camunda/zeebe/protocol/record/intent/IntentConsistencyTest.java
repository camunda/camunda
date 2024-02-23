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
package io.camunda.zeebe.protocol.record.intent;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

public class IntentConsistencyTest {

  private static final Collection<String> IGNORED =
      Arrays.asList(Intent.UNKNOWN.getClass().getName());

  @Test
  void listOfIntentClassesIsComplete() {
    // given
    final Collection<String> expectedIntentClasses =
        Intent.INTENT_CLASSES.stream().map(Class::getName).collect(Collectors.toSet());

    // when + then
    final SoftAssertions softly = new SoftAssertions();
    try (final ScanResult scanResult =
        new ClassGraph().enableClassInfo().acceptPackages("io.camunda.zeebe").scan()) {

      final Set<ClassInfo> intentClasses =
          scanResult.getClassesImplementing(Intent.class).stream()
              .filter(ClassInfo::isStandardClass)
              .collect(Collectors.toSet());

      for (final ClassInfo intentClassInfo : intentClasses) {

        final boolean removed = expectedIntentClasses.remove(intentClassInfo.getName());

        if (!removed && !IGNORED.contains(intentClassInfo.getName())) {
          /* if this fails, add the failing class either to Intent.INTENT_CLASSES
           * or to IGNORED
           */
          softly.fail(
              "Class " + intentClassInfo.getName() + " is not part of Intent.INTENT_CLASSES");
        }
      }

      softly.assertAll();
    }
  }
}
