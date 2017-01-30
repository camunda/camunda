package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.CredentialsDto;
import org.camunda.optimize.test.AbstractJerseyTest;
import org.junit.Test;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Askar Akhmerov
 */

public class AuthenticationIT extends AbstractJerseyTest {

  @Test
  public void authenticateUser() throws Exception {
    CredentialsDto entity = new CredentialsDto();
    entity.setUsername("demo");
    entity.setPassword("demo");

    Response response = target("authentication")
        .request()
        .post(Entity.json(entity));
    assertThat(response.getStatus(),is(200));
  }

  @Override
  protected String getContextLocation() {
    return "classpath:it-applicationContext.xml";
  }
}