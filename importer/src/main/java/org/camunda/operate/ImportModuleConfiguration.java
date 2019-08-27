package org.camunda.operate;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = "org.camunda.operate.zeebeimport")
@ConditionalOnProperty(name = "camunda.operate.importerEnabled", havingValue = "true", matchIfMissing = true)
public class ImportModuleConfiguration {
}
