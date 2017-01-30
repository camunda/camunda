package org.camunda.optimize.rest.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.HttpHeaders;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Askar Akhmerov
 */
public class AuthenticationUtilTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void getToken() throws Exception {
    ContainerRequestContext requestMock = Mockito.mock(ContainerRequestContext.class);
    Mockito.when(requestMock.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer test");
    assertThat(AuthenticationUtil.getToken(requestMock),is("test"));
  }

  @Test
  public void getTokenException() throws Exception {
    ContainerRequestContext requestMock = Mockito.mock(ContainerRequestContext.class);
    thrown.expect(NotAuthorizedException.class);
    AuthenticationUtil.getToken(requestMock);
  }

}