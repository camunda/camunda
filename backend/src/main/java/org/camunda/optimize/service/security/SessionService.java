package org.camunda.optimize.service.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.engine.GroupDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.camunda.optimize.rest.util.AuthenticationUtil.getSessionIssuer;

@Component
public class SessionService {

  private Logger logger = LoggerFactory.getLogger(getClass());

  private static ConcurrentHashMap<String, Session> userSessions = new ConcurrentHashMap<>();
  private Random secureRandom = new SecureRandom();
  private final static int SECRET_LENGTH = 16;

  @Autowired
  private ConfigurationService configurationService;

  public boolean isValidToken(String token) {
    Optional<String> username = getSessionIssuer(token);
    if (username.isPresent()) {
      Session session = userSessions.get(username.get());
      if (session != null) {
        return session.isTokenValid(token);
      }
    }
    logger.debug("Error while validating authentication token [{}]. " +
      "User [{}] is not logged in!", token, username);
    return false;
  }

  public boolean hasTokenExpired(String token) {
    Optional<String> username = getSessionIssuer(token);
    if (username.isPresent()) {
      Session session = userSessions.get(username.get());
      if (session != null) {
        return session.hasTokenExpired(token);
      }
    }
    return false;
  }

  private Algorithm generateAlgorithm() {
    byte[] secretBytes = new byte[SECRET_LENGTH];
    secureRandom.nextBytes(secretBytes);
    return Algorithm.HMAC256(secretBytes);
  }

  public void expireToken(String token) {
    Optional<String> username = getSessionIssuer(token);
    username.ifPresent(user -> userSessions.remove(user));
  }

  public void updateExpiryDate(String token) {
    Optional<String> username = getSessionIssuer(token);
    username.ifPresent(
      user -> userSessions.computeIfPresent(user, (u, session) -> {
        session.updateExpiryDate();
        return session;
      })
    );
  }

  public boolean isAuthorizedToSeeDefinition(String username, String processDefinitionKey) {
    if(userSessions.containsKey(username)) {
      Session session = userSessions.get(username);
      return session.isAuthorizedToSeeDefinition(processDefinitionKey);
    }
    return false;
  }

  public String createSessionAndReturnSecurityToken(String username, EngineContext engineContext) {

    Algorithm hashingAlgorithm = generateAlgorithm();
    String token = JWT.create()
      .withIssuer(username)
      .sign(hashingAlgorithm);

    JWTVerifier verifier = JWT.require(hashingAlgorithm)
      .withIssuer(username)
      .build(); //Reusable verifier instance

    TokenVerifier tokenVerifier = new TokenVerifier(configurationService.getTokenLifeTime(), verifier);
    DefinitionAuthorizations definitionAuthorizations = retrieveDefinitionAuthorizations(username, engineContext);
    Session session = new Session(tokenVerifier, definitionAuthorizations);
    userSessions.put(username, session);

    return token;
  }

  public void updateDefinitionAuthorizations(String username, EngineContext engineContext) {

    DefinitionAuthorizations definitionAuthorizations = retrieveDefinitionAuthorizations(username, engineContext);
    userSessions.computeIfPresent(username, (__, session) -> {
      session.updateDefinitionAuthorizations(definitionAuthorizations);
      return session;
    });
  }

  private DefinitionAuthorizations retrieveDefinitionAuthorizations(String username, EngineContext engineContext) {

    List<GroupDto> groups = engineContext.getAllGroupsOfUser(username);
    List<AuthorizationDto> allDefinitionAuthorizations = engineContext.getAllProcessDefinitionAuthorizations();
    List<AuthorizationDto> groupAuthorizations = extractGroupAuthorizations(groups, allDefinitionAuthorizations);
    List<AuthorizationDto> userAuthorizations = extractUserAuthorizations(username, allDefinitionAuthorizations);

    return new DefinitionAuthorizations(allDefinitionAuthorizations, groupAuthorizations, userAuthorizations);
  }

  private List<AuthorizationDto> extractGroupAuthorizations(List<GroupDto> groupsOfUser,
                                                            List<AuthorizationDto> allAuthorizations) {
    Set<String> groupIds = groupsOfUser.stream().map(GroupDto::getId).collect(Collectors.toSet());
    return allAuthorizations
      .stream()
      .filter(a -> groupIds.contains(a.getGroupId()))
      .collect(Collectors.toList());
  }

  private List<AuthorizationDto> extractUserAuthorizations(String username,
                                                           List<AuthorizationDto> allAuthorizations) {
    return allAuthorizations
      .stream()
      .filter(a -> username.equals(a.getUserId()))
      .collect(Collectors.toList());
  }



  public ConfigurationService getConfigurationService() {
    return configurationService;
  }

  public void setConfigurationService(ConfigurationService configurationService) {
    this.configurationService = configurationService;
  }

}
