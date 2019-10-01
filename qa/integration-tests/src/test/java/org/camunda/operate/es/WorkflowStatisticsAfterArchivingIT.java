package org.camunda.operate.es;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import static org.junit.Assume.assumeFalse;

public class WorkflowStatisticsAfterArchivingIT extends WorkflowStatisticsIT {

  @Rule
  public TestName name = new TestName();

  @Before
  public void before() {
    assumeFalse(name.getMethodName().startsWith("testFail"));
    super.before();
  }

  @Override
  protected void createData(Long workflowKey) {
    super.createData(workflowKey);
    runArchiving();
  }

}
