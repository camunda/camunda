package org.camunda.optimize.rest.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Cookie;
import java.util.Collections;
import java.util.Map;

import static org.camunda.optimize.rest.util.AuthenticationUtil.OPTIMIZE_AUTHORIZATION;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;


public class AuthenticationUtilTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void getToken() {
    // given
    String authorizationHeader = String.format("Bearer %s", "test");
    Cookie cookie = new Cookie(OPTIMIZE_AUTHORIZATION, authorizationHeader);
    Map<String, Cookie> cookies = Collections.singletonMap(OPTIMIZE_AUTHORIZATION, cookie);
    ContainerRequestContext requestMock = Mockito.mock(ContainerRequestContext.class);
    Mockito.when(requestMock.getCookies()).thenReturn(cookies);

    // when
    String token = AuthenticationUtil.getToken(requestMock);

    // then
    assertThat(token, is("test"));
  }

  @Test
  public void getTokenException() throws Exception {
    ContainerRequestContext requestMock = Mockito.mock(ContainerRequestContext.class);
    thrown.expect(NotAuthorizedException.class);
    AuthenticationUtil.getToken(requestMock);
  }

}