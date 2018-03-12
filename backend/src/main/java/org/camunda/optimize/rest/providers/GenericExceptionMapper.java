package org.camunda.optimize.rest.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.rest.util.RestResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;


/**
 * @author Askar Akhmerov
 */
@Provider
public class GenericExceptionMapper implements ExceptionMapper<Throwable> {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private ObjectMapper objectMapper;

  @Override
  public Response toResponse(Throwable throwable) {
    logger.error("Mapping REST error", throwable);
    return RestResponseUtil.buildServerErrorResponse(throwable, objectMapper);
  }
}
