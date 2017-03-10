package org.camunda.optimize.rest;

import org.camunda.optimize.dto.engine.AuthenticationResultDto;
import org.camunda.optimize.dto.optimize.CredentialsDto;
import org.camunda.optimize.rest.util.AuthenticationUtil;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.test.AbstractJerseyTest;
import org.camunda.optimize.test.mock.AuthenticationHelper;
import org.elasticsearch.client.transport.TransportClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/applicationContext.xml" })
public class AuthenticationRestServiceTest extends AbstractJerseyTest {

  @Autowired
  private TransportClient esClientMock;

  @Autowired
  private Client client;

  @Autowired
  private ConfigurationService configurationService;


  private void setUpEngineClientFailingAuthenticationMocks() {
    AuthenticationResultDto authenticationStub = new AuthenticationResultDto();
    authenticationStub.setAuthenticated(false);
    setupEngineClientAuthentication(authenticationStub);
  }

  private void setupEngineClientAuthentication(AuthenticationResultDto authenticationStub) {
    WebTarget mockTarget = Mockito.mock(WebTarget.class);
    Mockito.when(mockTarget.path(Mockito.anyString())).thenReturn(mockTarget);
    Invocation.Builder builderMock = Mockito.mock(Invocation.Builder.class);
    Response responseMock = Mockito.mock(Response.class);
    Mockito.when(responseMock.getStatus()).thenReturn(200);
    Mockito.when(responseMock.readEntity(AuthenticationResultDto.class)).thenReturn(authenticationStub);
    Mockito.when(builderMock.post(Mockito.any())).thenReturn(responseMock);
    Mockito.when(mockTarget.request(Mockito.anyString())).thenReturn(builderMock);
    Mockito.doReturn(mockTarget).when(client).target(Mockito.anyString());
  }

  @Test
  public void authenticateUserUsingES() throws Exception {
    setUpEngineClientFailingAuthenticationMocks();
    //when
    Response response = authenticateAdminPlain();

    //then
    assertThat(response.getStatus(),is(200));
    String responseEntity = response.readEntity(String.class);
    assertThat(responseEntity,is(notNullValue()));
  }

  protected Response authenticateAdminPlain() {
    CredentialsDto entity = new CredentialsDto();
    entity.setUsername("admin");
    entity.setPassword("admin");

    AuthenticationHelper.setupPositiveUserQuery(esClientMock,configurationService);

    return target("authentication")
        .request()
        .post(Entity.json(entity));
  }

  @Test
  public void logout() throws Exception {
    setUpEngineClientFailingAuthenticationMocks();
    //given
    Response response = authenticateAdminPlain();
    String token = response.readEntity(String.class);

    //when
    Response logoutResponse = target("authentication/logout")
        .request()
        .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
        .get();

    //then
    assertThat(logoutResponse.getStatus(),is(200));
    String responseEntity = logoutResponse.readEntity(String.class);
    assertThat(responseEntity,is("OK"));
  }

  @Test
  public void securingRestApiWorksWithProxy() throws Exception {
    setUpEngineClientFailingAuthenticationMocks();
    //given
    Response response = authenticateAdminPlain();
    String token = response.readEntity(String.class);

    //when
    Response testResponse = target("authentication/test")
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Basic ZGVtbzpkZW1v")
        .header(AuthenticationUtil.OPTIMIZE_AUTHORIZATION_HEADER,"Bearer " + token)
        .get();

    //then
    assertThat(testResponse.getStatus(),is(200));
    String responseEntity = testResponse.readEntity(String.class);
    assertThat(responseEntity,is("OK"));
  }

  @Test
  public void logoutSecure() throws Exception {

    //when
    Response logoutResponse = target("authentication/logout")
        .request()
        .header(HttpHeaders.AUTHORIZATION,"Bearer " + "randomToken")
        .get();

    //then
    assertThat(logoutResponse.getStatus(),is(401));
  }

  @Test
  public void testAuthenticateUserUsingEngine () {
    setUpEngineClientPassingAuthenticationMocks();
    //when
    Response response = authenticateAdminPlain();

    //then
    assertThat(response.getStatus(),is(200));
    String responseEntity = response.readEntity(String.class);
    assertThat(responseEntity,is(notNullValue()));
  }

  private void setUpEngineClientPassingAuthenticationMocks() {
    AuthenticationResultDto authenticationStub = new AuthenticationResultDto();
    authenticationStub.setAuthenticated(true);
    setupEngineClientAuthentication(authenticationStub);
  }

}