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

package io.camunda.zeebe.model.bpmn.util;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.InputStream;
import org.camunda.bpm.model.xml.impl.util.IoUtil;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/**
 * @author Daniel Meyer
 */
public class ParseBpmnModelRule extends TestWatcher {

  protected BpmnModelInstance bpmnModelInstance;

  @Override
  protected void starting(final Description description) {

    if (description.getAnnotation(BpmnModelResource.class) != null) {

      final Class<?> testClass = description.getTestClass();
      final String methodName = description.getMethodName();

      final String resourceFolderName = testClass.getName().replaceAll("\\.", "/");
      final String bpmnResourceName = resourceFolderName + "." + methodName + ".bpmn";

      final InputStream resourceAsStream =
          getClass().getClassLoader().getResourceAsStream(bpmnResourceName);
      try {
        bpmnModelInstance = Bpmn.readModelFromStream(resourceAsStream);
      } finally {
        IoUtil.closeSilently(resourceAsStream);
      }
    }
  }

  public BpmnModelInstance getBpmnModel() {
    return bpmnModelInstance;
  }
}
