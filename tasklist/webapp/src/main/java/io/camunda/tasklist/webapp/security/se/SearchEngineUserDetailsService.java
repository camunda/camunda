/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.webapp.security.se;

import static io.camunda.tasklist.util.CollectionUtil.map;
import static io.camunda.tasklist.webapp.security.TasklistProfileService.IDENTITY_AUTH_PROFILE;
import static io.camunda.tasklist.webapp.security.TasklistProfileService.SSO_AUTH_PROFILE;

import io.camunda.tasklist.entities.UserEntity;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import io.camunda.tasklist.webapp.security.se.store.UserStore;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Configuration
@Component
@Profile("!" + SSO_AUTH_PROFILE + " & !" + IDENTITY_AUTH_PROFILE)
public class SearchEngineUserDetailsService implements UserDetailsService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(SearchEngineUserDetailsService.class);

  @Autowired private UserStore userStore;

  @Autowired private TasklistProperties tasklistProperties;

  @Bean
  public PasswordEncoder getPasswordEncoder() {
    return new BCryptPasswordEncoder();
  }

  public void initializeUsers() {
    final boolean createSchema =
        TasklistProperties.ELASTIC_SEARCH.equalsIgnoreCase(tasklistProperties.getDatabase())
            ? tasklistProperties.getElasticsearch().isCreateSchema()
            : tasklistProperties.getOpenSearch().isCreateSchema();

    if (createSchema) {
      final String userId = tasklistProperties.getUserId();
      final String displayName = tasklistProperties.getDisplayName();
      final String password = tasklistProperties.getPassword();

      final String readerUserId = tasklistProperties.getReaderUserId();
      final String readerDisplayName = tasklistProperties.getReaderDisplayName();
      final String readerPassword = tasklistProperties.getReaderPassword();

      final String operatorUserId = tasklistProperties.getOperatorUserId();
      final String operatorDisplayName = tasklistProperties.getOperatorDisplayName();
      final String operatorPassword = tasklistProperties.getOperatorPassword();

      final List<String> roles = tasklistProperties.getRoles();
      if (!userExists(userId)) {
        addUserWith(userId, displayName, password, roles);
      }
      if (!userExists(readerUserId)) {
        addUserWith(readerUserId, readerDisplayName, readerPassword, List.of(Role.READER.name()));
      }
      if (!userExists(operatorUserId)) {
        addUserWith(
            operatorUserId, operatorDisplayName, operatorPassword, List.of(Role.OPERATOR.name()));
      }
    }
  }

  private boolean userExists(String userId) {
    try {
      return userStore.getByUserId(userId) != null;
    } catch (Exception t) {
      return false;
    }
  }

  SearchEngineUserDetailsService addUserWith(
      final String userId,
      final String displayName,
      final String password,
      final List<String> roles) {
    LOGGER.info("Create user with userId {}", userId);
    final String passwordEncoded = getPasswordEncoder().encode(password);
    final UserEntity userEntity =
        new UserEntity()
            .setId(userId)
            .setUserId(userId)
            .setDisplayName(displayName)
            .setPassword(passwordEncoded)
            .setRoles(roles);
    userStore.create(userEntity);
    return this;
  }

  @Override
  public User loadUserByUsername(String username) throws UsernameNotFoundException {
    try {
      final UserEntity userEntity = userStore.getByUserId(username);
      return new User(
              userEntity.getUserId(),
              userEntity.getPassword(),
              map(userEntity.getRoles(), Role::fromString))
          .setDisplayName(userEntity.getDisplayName())
          .setRoles(map(userEntity.getRoles(), Role::fromString));
    } catch (NotFoundApiException e) {
      throw new UsernameNotFoundException(
          String.format("User with user id '%s' not found.", username), e);
    }
  }
}
