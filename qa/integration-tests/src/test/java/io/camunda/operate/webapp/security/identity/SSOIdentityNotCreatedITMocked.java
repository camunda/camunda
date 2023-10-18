/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.identity;

import io.camunda.operate.property.OperateProperties;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunWith(SpringRunner.class)
@ActiveProfiles({"sso-auth"})
@SpringBootTest(
    classes = {
        IdentityConfigurer.class,
        OperateProperties.class},
    properties = {
        //when resource permissions are disabled, no Identity bean should be created
        OperateProperties.PREFIX + ".identity.resourcePermissionsEnabled = false"
    }
)
public class SSOIdentityNotCreatedITMocked {

  @Autowired
  private ApplicationContext applicationContext;

  @Test
  public void testIdentityIsCreated() {
    assertThatThrownBy(() -> applicationContext.getBean("saasIdentity")).isInstanceOf(NoSuchBeanDefinitionException.class);
    assertThatThrownBy(() -> applicationContext.getBean("permissionsService")).isInstanceOf(NoSuchBeanDefinitionException.class);
  }

}
