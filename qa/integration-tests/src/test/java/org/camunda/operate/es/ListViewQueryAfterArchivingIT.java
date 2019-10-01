package org.camunda.operate.es;

public class ListViewQueryAfterArchivingIT extends ListViewQueryIT {

  @Override
  protected void createData() {
    super.createData();
    runArchiving();
  }
}
