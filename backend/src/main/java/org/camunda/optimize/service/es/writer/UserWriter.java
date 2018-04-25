package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.user.CredentialsDto;
import org.camunda.optimize.dto.optimize.query.user.OptimizeUserWithPassword;
import org.camunda.optimize.dto.optimize.query.user.PermissionsDto;
import org.camunda.optimize.service.es.schema.type.UserType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.engine.VersionConflictEngineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.type.UserType.LAST_MODIFIER;
import static org.camunda.optimize.service.es.schema.type.UserType.PASSWORD;
import static org.camunda.optimize.service.es.schema.type.UserType.PERMISSIONS;

@Component
public class UserWriter {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private Client esclient;
  @Autowired
  private ConfigurationService configurationService;
  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private DateTimeFormatter dateTimeFormatter;

  public void createNewUser(CredentialsDto userPassword, String creatorId) {
    logger.debug("Writing new user to Elasticsearch");

    OffsetDateTime now = OffsetDateTime.now();
    OptimizeUserWithPassword user = new OptimizeUserWithPassword();
    user.setId(userPassword.getId());
    user.setPassword(userPassword.getPassword());
    user.setCreatedAt(now);
    user.setCreatedBy(creatorId);
    user.setLastLoggedIn(null);
    user.setLastModified(now);
    user.setLastModifier(creatorId);
    user.setPermissions(new PermissionsDto());


    try {
      esclient
        .prepareIndex(
          configurationService.getOptimizeIndex(configurationService.getElasticSearchUsersType()),
          configurationService.getElasticSearchUsersType(),
          user.getId()
        )
        .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
        .setOpType(DocWriteRequest.OpType.CREATE)
        .setSource(objectMapper.writeValueAsString(user), XContentType.JSON)
        .get();
    } catch (JsonProcessingException e) {
      String errorMessage = "Could not create new user with id [" + user.getId() + "]";
      logger.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (VersionConflictEngineException e) {
      String errorMessage = "Could not create new user with id [" + user.getId() + "]. User already exists.";
      logger.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    logger.debug("User with id [{}] has successfully been created.", user.getId());
  }

  public void deleteUser(String userId) {
    logger.debug("Deleting user with id [{}]", userId);
    esclient.prepareDelete(
        configurationService.getOptimizeIndex(configurationService.getElasticSearchUsersType()),
        configurationService.getElasticSearchUsersType(),
        userId
      )
      .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
      .get();
  }

  public void updatePermissions(String userId, PermissionsDto updatedPermissions, String modifierId) {
    logger.debug("Updating permissions of user with id [{}] in Elasticsearch", userId);

    Map updatePermissionsAsString = objectMapper.convertValue(updatedPermissions, Map.class);

    Map<String, Object> map = new HashMap<>();
    map.put(UserType.LAST_MODIFIED, currentDateAsString());
    map.put(LAST_MODIFIER, modifierId);
    map.put(PERMISSIONS, updatePermissionsAsString);

    try {
      UpdateResponse updateResponse = esclient
        .prepareUpdate(
          configurationService.getOptimizeIndex(configurationService.getElasticSearchUsersType()),
          configurationService.getElasticSearchUsersType(),
          userId
        )
        .setDoc(map)
        .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
        .get();

      if (updateResponse.getShardInfo().getFailed() > 0) {
        throw new OptimizeRuntimeException("Was not able to update user permissions!");
      }
    } catch (Exception e) {
      String errorMessage = "Was not able to update permissions of user with id [" + userId + "]";
      logger.error(errorMessage);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  private String currentDateAsString() {
    return dateTimeFormatter.format(LocalDateUtil.getCurrentDateTime());
  }

  public void updatePassword(String userId, String password, String modifierId) {
    logger.debug("Updating password of user with id [{}] in Elasticsearch", userId);

    Map<String, Object> map = new HashMap<>();
    map.put(UserType.LAST_MODIFIED, currentDateAsString());
    map.put(LAST_MODIFIER, modifierId);
    map.put(PASSWORD, password);

    try {
      UpdateResponse updateResponse = esclient
        .prepareUpdate(
          configurationService.getOptimizeIndex(configurationService.getElasticSearchUsersType()),
          configurationService.getElasticSearchUsersType(),
          userId
        )
        .setDoc(map)
        .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
        .get();

      if (updateResponse.getShardInfo().getFailed() > 0) {
        throw new OptimizeRuntimeException("Was not able to update user password!");
      }
    } catch (Exception e) {
      String errorMessage = "Was not able to update password of user with id [" + userId + "]";
      logger.error(errorMessage);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

  }
}
