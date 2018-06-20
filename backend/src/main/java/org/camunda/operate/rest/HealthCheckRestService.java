package org.camunda.operate.rest;

import org.camunda.operate.rest.dto.HealthStateDto;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Svetlana Dorokhova.
 */
@RestController
public class HealthCheckRestService {

  public static final String HEALTH_CHECK_URL = "/api/check";

  @RequestMapping(value = HEALTH_CHECK_URL, method = RequestMethod.GET)
  public HealthStateDto status() {
    return new HealthStateDto("OK");
  }

}
