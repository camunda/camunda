/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.page;

import java.util.Set;

public class IdSetBasedImportPage implements ImportPage {

  private Set<String> ids;

  public Set<String> getIds() {
    return ids;
  }

  public void setIds(Set<String> ids) {
    this.ids = ids;
  }
}
