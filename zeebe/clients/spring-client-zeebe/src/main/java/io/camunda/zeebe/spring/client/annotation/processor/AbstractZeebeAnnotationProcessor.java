package io.camunda.zeebe.spring.client.annotation.processor;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.spring.client.bean.ClassInfo;
import org.springframework.beans.factory.BeanNameAware;

public abstract class AbstractZeebeAnnotationProcessor implements BeanNameAware {

  private String beanName;

  public String getBeanName() {
    return beanName;
  }

  @Override
  public void setBeanName(String beanName) {
    this.beanName = beanName;
  }

  public abstract boolean isApplicableFor(ClassInfo beanInfo);

  public abstract void configureFor(final ClassInfo beanInfo);

  public abstract void start(ZeebeClient zeebeClient);

  public abstract void stop(ZeebeClient zeebeClient);
}
