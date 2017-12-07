package org.camunda.optimize.rest.providers;

import org.camunda.optimize.rest.util.RestResponseUtil;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;


/**
 * @author Askar Akhmerov
 */
@Provider
public class GenericExceptionMapper implements ExceptionMapper<Throwable> {
  @Override
  public Response toResponse(Throwable throwable) {
    return RestResponseUtil.buildServerErrorResponse(throwable);
  }
}
