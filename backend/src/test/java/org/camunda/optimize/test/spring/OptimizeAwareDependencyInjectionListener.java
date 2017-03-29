package org.camunda.optimize.test.spring;

import org.camunda.optimize.test.rule.EmbeddedOptimizeRule;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

/**
 * @author Askar Akhmerov
 */
public class OptimizeAwareDependencyInjectionListener extends DependencyInjectionTestExecutionListener {

  public static final String EMBEDDED_OPTIMIZE_RULE = "embeddedOptimizeRule";

  protected void injectDependencies(final TestContext testContext) throws Exception {
    Object bean = testContext.getTestInstance();
    AutowireCapableBeanFactory beanFactory = testContext.getApplicationContext().getAutowireCapableBeanFactory();
    //autowire from test context
    boolean allAutowired = true;
    try {
      beanFactory.autowireBeanProperties(bean, AutowireCapableBeanFactory.AUTOWIRE_NO, false);
    } catch (Exception e) {
      allAutowired = false;
    }
    //if there is a rule provided - inject from optimize
    boolean usingEmbeddedOptimize = bean.getClass().getField(EMBEDDED_OPTIMIZE_RULE) != null;
    if (usingEmbeddedOptimize) {
      EmbeddedOptimizeRule ruleInstance = (EmbeddedOptimizeRule)bean.getClass()
          .getField(EMBEDDED_OPTIMIZE_RULE).get(bean);
      beanFactory = ruleInstance.getApplicationContext().getAutowireCapableBeanFactory();
      //autowire from optimize
      beanFactory.autowireBeanProperties(bean, AutowireCapableBeanFactory.AUTOWIRE_NO, false);
    }

    beanFactory.initializeBean(bean, testContext.getTestClass().getName());
    testContext.removeAttribute(REINJECT_DEPENDENCIES_ATTRIBUTE);
  }

}
