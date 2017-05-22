package org.camunda.optimize.rest.providers;

import org.springframework.stereotype.Component;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

/**
 * @author Askar Akhmerov
 */
@Provider
@Component
public class NoCacheFilter implements ContainerResponseFilter {
  private static final String NO_STORE = "no-store";

  @Override
  public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
    responseContext.getHeaders().putSingle(HttpHeaders.CACHE_CONTROL, NO_STORE);
  }
}
