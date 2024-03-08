package io.camunda.zeebe.spring.client.annotation;

import java.lang.annotation.*;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Variable {
  String DEFAULT_NAME = "$NULL$";

  String name() default DEFAULT_NAME;

  String value() default DEFAULT_NAME;
}
