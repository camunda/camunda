package org.camunda.optimize.service.engine.importing.index.page;

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
