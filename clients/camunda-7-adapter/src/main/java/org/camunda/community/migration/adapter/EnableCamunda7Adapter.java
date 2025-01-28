package org.camunda.community.migration.adapter;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

@Documented
@Target(TYPE)
@Retention(RUNTIME)
@Import(CamundaPlatform7AdapterConfig.class)
public @interface EnableCamunda7Adapter {}
