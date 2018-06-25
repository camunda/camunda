package org.camunda.operate.util;

import org.camunda.operate.TestApplication;
import org.camunda.operate.property.OperateProperties;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;


@RunWith(SpringRunner.class)
@SpringBootTest(
  classes = {TestApplication.class},
  properties = OperateProperties.PREFIX + ".startLoadingDataOnStartup = false")
@WebAppConfiguration
@TestExecutionListeners(listeners = DependencyInjectionTestExecutionListener.class, mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public abstract class OperateIntegrationTest {
}
