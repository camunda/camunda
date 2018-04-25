package org.camunda.optimize.service.user;

import org.camunda.optimize.dto.optimize.query.user.CredentialsDto;
import org.camunda.optimize.dto.optimize.query.user.OptimizeUserDto;
import org.camunda.optimize.dto.optimize.query.user.PermissionsDto;
import org.camunda.optimize.rest.queryparam.adjustment.QueryParamAdjustmentUtil;
import org.camunda.optimize.service.es.reader.UserReader;
import org.camunda.optimize.service.es.writer.UserWriter;
import org.camunda.optimize.service.util.ValidationHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MultivaluedMap;
import java.io.IOException;
import java.util.List;

@Component
public class UserService {

  @Autowired
  private UserWriter userWriter;

  @Autowired
  private UserReader userReader;

  public void createNewUser(CredentialsDto userPassword, String creatorId) {
    ValidationHelper.validate(userPassword);
    userWriter.createNewUser(userPassword, creatorId);
  }

  public void updatePermission(String userId, PermissionsDto updatedPermissions, String modifierId) {
    ValidationHelper.ensureNotEmpty("user id", userId);
    ValidationHelper.validate(updatedPermissions);
    userWriter.updatePermissions(userId, updatedPermissions, modifierId);

  }

  public void updatePassword(String userId, String password, String modifierId) {
    ValidationHelper.ensureNotEmpty("user id", userId);
    ValidationHelper.ensureNotEmpty("user password", password);
    userWriter.updatePassword(userId, password, modifierId);

  }

  public List<OptimizeUserDto> findAllUsers(MultivaluedMap<String, String> adjustmentsParameters) throws IOException {
    List<OptimizeUserDto> users = userReader.getAllUsers();
    users = QueryParamAdjustmentUtil.adjustUserResultsToQueryParameters(users, adjustmentsParameters);
    return users;
  }

  public OptimizeUserDto getUser(String userId) {
    ValidationHelper.ensureNotEmpty("user id", userId);
    return userReader.getUser(userId);
  }
  public void deleteUser(String userId) {
    ValidationHelper.ensureNotEmpty("user id", userId);
    userWriter.deleteUser(userId);
  }
}
