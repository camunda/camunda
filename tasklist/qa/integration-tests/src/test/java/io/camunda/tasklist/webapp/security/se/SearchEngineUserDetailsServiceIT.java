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

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.tasklist.es.RetryElasticsearchClient;
import io.camunda.tasklist.management.SearchEngineHealthIndicator;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TestElasticsearchSchemaManager;
import io.camunda.tasklist.schema.indices.UserIndex;
import io.camunda.tasklist.util.DatabaseTestExtension;
import io.camunda.tasklist.util.NoSqlHelper;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.tasklist.util.TestApplication;
import io.camunda.tasklist.webapp.security.WebSecurityConfig;
import io.camunda.tasklist.webapp.security.oauth.OAuth2WebConfigurer;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * This test tests that: 1. If we configure custom username and password, this user is added to
 * Elasticsearch 2. If we adjust firstname and lastname in Elasticsearch, this values are returned
 * by UserDetailsService
 */
@SpringBootTest(
    classes = {
      TestElasticsearchSchemaManager.class,
      TestApplication.class,
      SearchEngineHealthIndicator.class,
      WebSecurityConfig.class,
      OAuth2WebConfigurer.class,
      RetryElasticsearchClient.class,
    },
    properties = {
      TasklistProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      TasklistProperties.PREFIX + ".archiver.rolloverEnabled = false",
      TasklistProperties.PREFIX + ".userId = user1",
      TasklistProperties.PREFIX + ".password = psw1",
      "graphql.servlet.websocket.enabled=false"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SearchEngineUserDetailsServiceIT extends TasklistIntegrationTest {

  private static final String TEST_USERNAME = "user1";
  private static final String TEST_PASSWORD = "psw1";
  private static final String TEST_FIRSTNAME = "Quentin";
  private static final String TEST_LASTNAME = "Tarantino ";

  @RegisterExtension @Autowired public DatabaseTestExtension databaseTestExtension;

  @Autowired private SearchEngineUserDetailsService userDetailsService;

  @Autowired private UserIndex userIndex;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private NoSqlHelper noSqlHelper;

  @BeforeEach
  public void setUp() {
    super.before();
    databaseTestExtension.refreshTasklistIndices();
  }

  @AfterEach
  public void deleteUser() throws IOException {
    deleteById(TEST_USERNAME);
  }

  @Test
  public void testCustomUserIsAdded() {
    // when
    userDetailsService.initializeUsers();
    databaseTestExtension.refreshTasklistIndices();

    // and
    updateUserRealName();

    // then
    final UserDetails userDetails = userDetailsService.loadUserByUsername(TEST_USERNAME);
    assertThat(userDetails).isInstanceOf(User.class);
    final User testUser = (User) userDetails;
    assertThat(testUser.getUsername()).isEqualTo(TEST_USERNAME);
    assertThat(passwordEncoder.matches(TEST_PASSWORD, testUser.getPassword())).isTrue();
    assertThat(testUser.getUserId()).isEqualTo("user1");
    assertThat(testUser.getDisplayName()).isEqualTo(TEST_FIRSTNAME + " " + TEST_LASTNAME);
  }

  private void updateUserRealName() {
    try {
      final Map<String, Object> jsonMap = new HashMap<>();
      jsonMap.put(UserIndex.DISPLAY_NAME, String.format("%s %s", TEST_FIRSTNAME, TEST_LASTNAME));
      noSqlHelper.update(userIndex.getFullQualifiedName(), TEST_USERNAME, jsonMap);
      databaseTestExtension.refreshTasklistIndices();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void deleteById(String id) throws IOException {
    noSqlHelper.delete(userIndex.getFullQualifiedName(), id);
  }
}
