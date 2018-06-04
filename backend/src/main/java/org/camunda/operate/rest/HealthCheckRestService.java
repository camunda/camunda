package org.camunda.operate.rest;

import org.camunda.operate.rest.dto.HealthStateDto;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Svetlana Dorokhova.
 */
@RestController
public class HealthCheckRestService {

  @RequestMapping(value = "/check")
  public HealthStateDto status() {
    return new HealthStateDto("OK");
  }

}
