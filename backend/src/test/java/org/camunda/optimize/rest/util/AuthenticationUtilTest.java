package org.camunda.optimize.rest.util;

import org.junit.Test;
import org.mockito.Mockito;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Cookie;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.camunda.optimize.rest.util.AuthenticationUtil.OPTIMIZE_AUTHORIZATION;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class AuthenticationUtilTest {

  @Test
  public void getToken() {
    // given
    String authorizationHeader = String.format("Bearer %s", "test");
    Cookie cookie = new Cookie(OPTIMIZE_AUTHORIZATION, authorizationHeader);
    Map<String, Cookie> cookies = Collections.singletonMap(OPTIMIZE_AUTHORIZATION, cookie);
    ContainerRequestContext requestMock = Mockito.mock(ContainerRequestContext.class);
    Mockito.when(requestMock.getCookies()).thenReturn(cookies);

    // when
    Optional<String> token = AuthenticationUtil.getToken(requestMock);

    // then
    assertThat(token.isPresent(), is(true));
    assertThat(token.get(), is("test"));
  }

  @Test
  public void getTokenException() {
    ContainerRequestContext requestMock = Mockito.mock(ContainerRequestContext.class);
    assertThat(AuthenticationUtil.getToken(requestMock), is(Optional.empty()));
  }

}