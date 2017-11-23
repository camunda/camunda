package org.camunda.optimize.service.security;

import org.camunda.optimize.service.exceptions.InvalidTokenException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.time.LocalDateTime;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/unit/applicationContext.xml"})
public class TokenServiceTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private TokenService tokenService;

  @After
  public void resetClock() {
    LocalDateUtil.reset();
  }

  @Test
  public void tokenValidationWorksDirectlyAfterIssuingIt() throws InvalidTokenException {
    // when
    String token = tokenService.issueToken("Kermit");

    // then
    tokenService.validateToken(token);
  }

  @Test
  public void tokenGenerationDoesNotCreateSameTokenTwice() throws InvalidTokenException {
    // when
    String firstToken = tokenService.issueToken("Kermit");
    tokenService.issueToken("Kermit");

    // then
    thrown.expect(InvalidTokenException.class);
    tokenService.validateToken(firstToken);
  }

  @Test
  public void tokenShouldExpireAfterConfiguredTime() throws InvalidTokenException {
    // given
    int expiryTime = configurationService.getLifetime();

    // then
    thrown.expect(InvalidTokenException.class);

    // when
    String token = tokenService.issueToken("Kermit");
    LocalDateUtil.setCurrentTime(get1SecondAfterExpiryTime(expiryTime));
    tokenService.validateToken(token);
  }

  @Test
  public void validationShouldNotIncreaseMaximumLifetimeOfToken() throws InvalidTokenException {
    // given
    int expiryTime = configurationService.getLifetime();

    // then
    thrown.expect(InvalidTokenException.class);

    // when
    String token = tokenService.issueToken("Kermit");
    tokenService.validateToken(token);
    LocalDateUtil.setCurrentTime(get1SecondAfterExpiryTime(expiryTime));
    tokenService.validateToken(token);
  }

  private LocalDateTime get1SecondAfterExpiryTime(int expiryTime) {
    return LocalDateUtil.getCurrentDateTime().plusMinutes(expiryTime).plusSeconds(1);
  }
}
