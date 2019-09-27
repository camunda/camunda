/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.sso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.operate.util.CollectionUtil.asMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import com.auth0.AuthenticationController;
import com.auth0.AuthorizeUrl;
import com.auth0.IdentityVerificationException;
import com.auth0.Tokens;


@RunWith(SpringRunner.class)
@SpringBootTest(
  classes = {
      TestApplicationWithNoBeans.class,SSOWebSecurityConfig.class,SSOController.class,TokenAuthentication.class,AuthenticationRestService.class
  },
  properties = {
      "camunda.operate.auth0.clientId=1",
      "camunda.operate.auth0.clientSecret=2",
      "camunda.operate.auth0.organization=3"
  },
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("sso-auth")
public class AuthenticationTest {

  @LocalServerPort
  int randomServerPort;
  
  @Autowired
  TestRestTemplate testRestTemplate;
 
  @Autowired
  SSOWebSecurityConfig ssoConfig;
  
  @MockBean
  AuthenticationController authenticationController;
  
  @Before
  public void setUp() throws Throwable{
    // mock building authorizeUrl
    AuthorizeUrl mockedAuthorizedUrl = mock(AuthorizeUrl.class);
    given(authenticationController.buildAuthorizeUrl(isNotNull(), isNotNull())).willReturn(mockedAuthorizedUrl);
    given(mockedAuthorizedUrl.withAudience(isNotNull())).willReturn(mockedAuthorizedUrl);
    given(mockedAuthorizedUrl.withScope(isNotNull())).willReturn(mockedAuthorizedUrl);
    given(mockedAuthorizedUrl.build()).willReturn("https://login.cloud.ultrawombat.com/authorize?redirect_uri=http://localhost:58117/sso-callback&client_id=1&audience=https://camunda-dev.eu.auth0.com/userinfo"); 
  }
  
  @Test
  public void testLoginSuccess() throws Exception { 
    // Step 1 try to access document root
    ResponseEntity<String> response = get(SSOWebSecurityConfig.ROOT);
    HttpEntity<?> cookies = httpEntityWithCookie(response);
    
    assertThatRequestIsRedirectedTo(response,urlFor(SSOWebSecurityConfig.LOGIN_RESOURCE));

    // Step 2 Get Login provider url
    response = get(SSOWebSecurityConfig.LOGIN_RESOURCE,cookies);   
    assertThat(redirectLocationIn(response)).contains(
        ssoConfig.getDomain(),
        SSOWebSecurityConfig.CALLBACK_URI,
        ssoConfig.getClientId(),
        ssoConfig.getBackendDomain()
    );
    // Step 3 Call back uri with valid userinfos
    // mock building tokens
    given(authenticationController.handle(isNotNull())).willReturn(tokensFrom(ssoConfig.getClaimName(), ssoConfig.getOrganization()));
    
    response = get(SSOWebSecurityConfig.CALLBACK_URI,cookies);
    assertThatRequestIsRedirectedTo(response, urlFor(SSOWebSecurityConfig.ROOT));
    
    response = get(SSOWebSecurityConfig.ROOT,cookies);
    // Check if access to url possible
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }
  
  @Test
  public void testLoginFailedWithNoPermissions() throws Exception { 
    // Step 1 try to access document root
    ResponseEntity<String> response = get(SSOWebSecurityConfig.ROOT);
    HttpEntity<?> cookies = httpEntityWithCookie(response);
    
    assertThatRequestIsRedirectedTo(response,urlFor(SSOWebSecurityConfig.LOGIN_RESOURCE));
    
    // Step 2 Get Login provider url
    response = get(SSOWebSecurityConfig.LOGIN_RESOURCE,cookies);   
    assertThat(redirectLocationIn(response)).contains(
        ssoConfig.getDomain(),
        SSOWebSecurityConfig.CALLBACK_URI,
        ssoConfig.getClientId(),
        ssoConfig.getBackendDomain()
    );
    // Step 3 Call back uri with invalid userdata  
    given(authenticationController.handle(isNotNull())).willReturn(tokensFrom(ssoConfig.getClaimName(), "wrong-organization"));
    
    response = get(SSOWebSecurityConfig.CALLBACK_URI,cookies);
    assertThat(redirectLocationIn(response)).contains(
        ssoConfig.getDomain(),
        "logout",
        ssoConfig.getClientId(),
        SSOWebSecurityConfig.NO_PERMISSION
    );
    
    response = get(SSOWebSecurityConfig.ROOT,cookies);
    // Check that access to url is not possible
    assertThatRequestIsRedirectedTo(response,urlFor(SSOWebSecurityConfig.LOGIN_RESOURCE));
  }
  
  @Test
  public void testLoginFailedWithOtherException() throws Exception { 
    // Step 1 try to access document root
    ResponseEntity<String> response = get(SSOWebSecurityConfig.ROOT);
    HttpEntity<?> cookies = httpEntityWithCookie(response);
    
    assertThatRequestIsRedirectedTo(response,urlFor(SSOWebSecurityConfig.LOGIN_RESOURCE));
    
    // Step 2 Get Login provider url
    response = get(SSOWebSecurityConfig.LOGIN_RESOURCE,cookies);   
    assertThat(redirectLocationIn(response)).contains(
        ssoConfig.getDomain(),
        SSOWebSecurityConfig.CALLBACK_URI,
        ssoConfig.getClientId(),
        ssoConfig.getBackendDomain()
    );
    // Step 3 Call back uri, but there is an IdentityVerificationException.
    doThrow(IdentityVerificationException.class).when(authenticationController).handle(any());
    
    response = get(SSOWebSecurityConfig.CALLBACK_URI,cookies);
    assertThatRequestIsRedirectedTo(response, urlFor(SSOWebSecurityConfig.NO_PERMISSION));
  }
  
  @Test
  public void testLogout() throws Throwable {
    // Step 1 Login
    ResponseEntity<String> response = get(SSOWebSecurityConfig.ROOT);
    HttpEntity<?> cookies = httpEntityWithCookie(response);
    response = get(SSOWebSecurityConfig.LOGIN_RESOURCE,cookies);    
    given(authenticationController.handle(isNotNull())).willReturn(tokensFrom(ssoConfig.getClaimName(), ssoConfig.getOrganization()));
    response = get(SSOWebSecurityConfig.CALLBACK_URI,cookies);
    response = get(SSOWebSecurityConfig.ROOT,cookies);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    // Step 2 logout
    response = get(SSOWebSecurityConfig.LOGOUT_RESOURCE, cookies);   
    assertThat(redirectLocationIn(response)).contains(
        ssoConfig.getDomain(),
        "logout",
        ssoConfig.getClientId(),
        urlFor(SSOWebSecurityConfig.ROOT)
    );
    // Redirected to Login 
    response = get(SSOWebSecurityConfig.ROOT);
    assertThatRequestIsRedirectedTo(response,urlFor(SSOWebSecurityConfig.LOGIN_RESOURCE));
  }
  
  @Test
  public void testLoginToAPIResource() throws Exception { 
    // Step 1 try to access user info
    String userInfoUrl = AuthenticationRestService.AUTHENTICATION_URL+"/user";
    ResponseEntity<String> response = get(userInfoUrl);
    assertThatRequestIsRedirectedTo(response,urlFor(SSOWebSecurityConfig.LOGIN_RESOURCE));
    
    // Save cookie for further requests
    HttpEntity<?> httpEntity = httpEntityWithCookie(response);
    
    // Step 2 Get Login provider url
    response = get(SSOWebSecurityConfig.LOGIN_RESOURCE,httpEntity);   
    
    assertThat(redirectLocationIn(response)).contains(
        ssoConfig.getDomain(),
        SSOWebSecurityConfig.CALLBACK_URI,
        ssoConfig.getClientId(),
        ssoConfig.getBackendDomain()
    );
    // Step 3 Call back uri
    given(authenticationController.handle(isNotNull())).willReturn(tokensFrom(ssoConfig.getClaimName(), ssoConfig.getOrganization()));
    
    response = get(SSOWebSecurityConfig.CALLBACK_URI,httpEntity);
    assertThatRequestIsRedirectedTo(response, urlFor(userInfoUrl));
    response = get(userInfoUrl,httpEntity);
    assertThat(response.getBody()).contains("\"lastname\":\"operate-testuser\"");
  }

  private HttpEntity<?> httpEntityWithCookie(ResponseEntity<String> response) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("Cookie", response.getHeaders().get("Set-Cookie").get(0));
    HttpEntity<?> httpEntity = new HttpEntity<>(new HashMap<>(),headers);
    return httpEntity;
  }
  
  @Test
  public void testAccessNoPermission() {
    ResponseEntity<String> response = get(SSOWebSecurityConfig.NO_PERMISSION);   
    assertThat(response.getBody()).contains("No permission for Operate");
  }
  
  protected void assertThatRequestIsRedirectedTo(ResponseEntity<?> response,String url) {
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(redirectLocationIn(response)).isEqualTo(url);
  }
  
  protected String redirectLocationIn(ResponseEntity<?> response) {
    return response.getHeaders().getLocation().toString();
  }
  
  protected ResponseEntity<String> get(String path){
    return testRestTemplate.getForEntity(path, String.class,new HashMap<String,String>());
  }
  
  protected ResponseEntity<String> get(String path,HttpEntity<?> requestEntity){
    return testRestTemplate.exchange(path, HttpMethod.GET, requestEntity, String.class);
  }
  
  protected String urlFor(String path) {
    return "http://localhost:"+randomServerPort+path;
  }
  
  protected Tokens tokensFrom(String claim,String organization) {
    String emptyJSONEncoded = toEncodedToken(Collections.EMPTY_MAP);
    long expiresInSeconds = System.currentTimeMillis()/1000+10000; // now + 10 seconds
    String accountData =  toEncodedToken(asMap( 
        claim, Arrays.asList(organization),
        "exp", expiresInSeconds,
        "name", "operate-testuser"
    ));
    return new Tokens("accessToken", emptyJSONEncoded+"."+accountData+"."+emptyJSONEncoded, "refreshToken", "type", 5L); 
  }
  
  protected String toEncodedToken(Map map) {
    return toBase64(toJSON(map));
  }
  
  protected String toBase64(String input) {
    return new String(Base64.getEncoder().encode(input.getBytes()));
  }
  
  protected String toJSON(Map map) {
    return new JSONObject(map).toString();
  }
}
