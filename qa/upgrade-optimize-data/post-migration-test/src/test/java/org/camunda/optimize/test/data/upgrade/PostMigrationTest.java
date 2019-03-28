package org.camunda.optimize.test.data.upgrade;

import org.camunda.optimize.dto.engine.CredentialsDto;
import org.camunda.optimize.rest.providers.OptimizeObjectMapperContextResolver;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.util.List;

import static org.camunda.optimize.service.security.AuthCookieService.OPTIMIZE_AUTHORIZATION;
import static org.camunda.optimize.service.security.AuthCookieService.createOptimizeAuthCookieValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class PostMigrationTest {
  private static Client client;
  private static String authHeader;

  @BeforeClass
  public static void init() {
    ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("optimizeDataUpgradeContext.xml");

    OptimizeObjectMapperContextResolver provider = ctx.getBean(OptimizeObjectMapperContextResolver.class);

    client = ClientBuilder.newClient().register(provider);
    authenticateDemo();
  }


  @Test
  public void retrieveAllReports() {
    Response response = client.target("http://localhost:8090/api/report")
      .request()
      .cookie(OPTIMIZE_AUTHORIZATION, authHeader)
      .get();

    List<Object> objects = response.readEntity(new GenericType<List<Object>>() {
    });
    assertThat(objects.size() > 0, is(true));
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void retrieveDashboards() {
    Response response = client.target("http://localhost:8090/api/dashboard")
      .request()
      .cookie(OPTIMIZE_AUTHORIZATION, authHeader)
      .get();

    List<Object> objects = response.readEntity(new GenericType<List<Object>>() {
    });
    assertThat(objects.size() > 0, is(true));
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void retrieveAlerts() {
    Response response = client.target("http://localhost:8090/api/alert")
      .request()
      .cookie(OPTIMIZE_AUTHORIZATION, authHeader)
      .get();

    List<Object> objects = response.readEntity(new GenericType<List<Object>>() {
    });
    assertThat(objects.size() > 0, is(true));
    assertThat(response.getStatus(), is(200));
  }

  private static void authenticateDemo() {
    CredentialsDto credentials = new CredentialsDto();
    credentials.setUsername("demo");
    credentials.setPassword("demo");

    Response response = client.target("http://localhost:8090/api/authentication")
      .request().post(Entity.json(credentials));

    authHeader = createOptimizeAuthCookieValue(response.readEntity(String.class));
  }
}
