package org.camunda.operate.util;

import org.camunda.operate.TestApplication;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;

/**
 * @author Svetlana Dorokhova.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {TestApplication.class})
@WebAppConfiguration
public abstract class OperateIntegrationTest {
}
