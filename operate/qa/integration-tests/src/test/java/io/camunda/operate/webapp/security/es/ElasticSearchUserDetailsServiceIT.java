/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.operate.webapp.security.es;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.UserIndex;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.SearchTestRule;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.util.searchrepository.TestSearchRepository;
import io.camunda.operate.webapp.security.auth.OperateUserDetailsService;
import io.camunda.operate.webapp.security.auth.User;
import java.io.IOException;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * This test tests that: 1. If we configure custom username and password, this user is added to
 * Elasticsearch 2. If we adjust firstname and lastname in Elasticsearch, these values are returned
 * by UserDetailsService
 */
@SpringBootTest(
    classes = {TestApplication.class},
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      OperateProperties.PREFIX + ".userId = " + ElasticSearchUserDetailsServiceIT.TEST_USER_ID,
      OperateProperties.PREFIX + ".displayName = User 1",
      OperateProperties.PREFIX + ".password = " + ElasticSearchUserDetailsServiceIT.TEST_PASSWORD,
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER"
    })
public class ElasticSearchUserDetailsServiceIT extends OperateAbstractIT {

  public static final String TEST_USER_ID = "user1";
  public static final String TEST_USER_DISPLAYNAME = "Quentin Tarantino";
  public static final String TEST_PASSWORD = "psw1";
  @Rule public SearchTestRule searchTestRule = new SearchTestRule();
  @Autowired private OperateUserDetailsService userDetailsService;
  @Autowired private TestSearchRepository testSearchRepository;
  @Autowired private UserIndex userIndex;
  @Autowired private PasswordEncoder passwordEncoder;

  @Before
  public void setUp() {
    searchTestRule.refreshOperateSearchIndices();
  }

  @After
  public void deleteUser() {
    deleteById(TEST_USER_ID);
  }

  @Test
  public void testCustomUserIsAdded() {
    // when
    userDetailsService.initializeUsers();
    searchTestRule.refreshOperateSearchIndices();

    // and
    updateUserRealName();

    // then
    User user = userDetailsService.loadUserByUsername(TEST_USER_ID);
    assertThat(user.getUsername()).isEqualTo(TEST_USER_ID);
    assertThat(passwordEncoder.matches(TEST_PASSWORD, user.getPassword())).isTrue();
    assertThat(user.getDisplayName()).isEqualTo(TEST_USER_DISPLAYNAME);
  }

  private void updateUserRealName() {
    try {
      Map<String, Object> jsonMap = Map.of(UserIndex.DISPLAY_NAME, TEST_USER_DISPLAYNAME);
      testSearchRepository.update(userIndex.getFullQualifiedName(), TEST_USER_ID, jsonMap);
      searchTestRule.refreshOperateSearchIndices();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void deleteById(String id) {
    try {
      testSearchRepository.deleteById(userIndex.getFullQualifiedName(), id);
    } catch (IOException ex) {
    }
  }
}
