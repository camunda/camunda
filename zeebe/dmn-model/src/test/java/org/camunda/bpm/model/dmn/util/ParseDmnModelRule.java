/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
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
package org.camunda.bpm.model.dmn.util;

import java.io.InputStream;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.bpm.model.xml.impl.util.IoUtil;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class ParseDmnModelRule implements BeforeEachCallback {

  protected DmnModelInstance dmnModelInstance;

  @Override
  public void beforeEach(ExtensionContext context) {

    final DmnModelResource dmnModelResource =
        context.getRequiredTestMethod().getAnnotation(DmnModelResource.class);

    if (dmnModelResource != null) {

      String resourcePath = dmnModelResource.resource();

      if (resourcePath.isEmpty()) {
        final Class<?> testClass = context.getRequiredTestClass();
        final String methodName = context.getRequiredTestMethod().getName();

        final String resourceFolderName = testClass.getName().replaceAll("\\.", "/");
        resourcePath = resourceFolderName + "." + methodName + ".dmn";
      }

      final InputStream resourceAsStream =
          getClass().getClassLoader().getResourceAsStream(resourcePath);
      try {
        dmnModelInstance = Dmn.readModelFromStream(resourceAsStream);
      } finally {
        IoUtil.closeSilently(resourceAsStream);
      }
    }
  }

  public DmnModelInstance getDmnModel() {
    return dmnModelInstance;
  }
}
