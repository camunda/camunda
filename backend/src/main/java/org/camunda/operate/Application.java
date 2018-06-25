package org.camunda.operate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
@EnableAutoConfiguration
public class Application {

  public static void main(String[] args) throws Exception {
    final SpringApplication springApplication = new SpringApplication(Application.class);
    springApplication.setAdditionalProfiles("auth");
    springApplication.run(args);
  }

}

