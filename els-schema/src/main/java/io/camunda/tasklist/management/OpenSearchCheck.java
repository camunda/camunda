/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.management;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/** TODO: implement in the scope of: https://github.com/camunda/tasklist/issues/3316 */
@Component
@Conditional(OpenSearchCondition.class)
public class OpenSearchCheck implements SearchEngineCheck {
  @Override
  public boolean indicesArePresent() {
    return true;
  }

  @Override
  public boolean isHealthy() {
    return true;
  }
}
