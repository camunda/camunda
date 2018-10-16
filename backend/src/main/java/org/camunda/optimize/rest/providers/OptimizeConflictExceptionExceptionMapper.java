package org.camunda.optimize.rest.providers;

import org.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import org.camunda.optimize.service.exceptions.OptimizeConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;


@Provider
public class OptimizeConflictExceptionExceptionMapper implements ExceptionMapper<OptimizeConflictException> {
  private final Logger logger = LoggerFactory.getLogger(OptimizeConflictExceptionExceptionMapper.class);

  @Override
  public Response toResponse(OptimizeConflictException conflictException) {
    logger.warn("Mapping OptimizeConflictException");
    return Response
      .status(Response.Status.CONFLICT)
      .entity(new ConflictResponseDto(conflictException.getConflictedItems()))
      .build();

  }
}
