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
package io.camunda.tasklist.webapp.security.identity;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.params.provider.Arguments.of;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.identity.sdk.Identity;
import io.camunda.tasklist.property.IdentityProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IdentityServiceTest {

  @Mock private Identity identity;
  @Spy private TasklistProperties tasklistProperties = new TasklistProperties();

  @InjectMocks private IdentityService instance;

  private static Stream<Arguments> getRedirectUriWhenTasklistIdentityRootUrlNotProvidedTestData() {
    return Stream.of(
        of("http", 80, "/some-path", "http://localhost/some-path/identity-callback"),
        of("http", 8089, "", "http://localhost:8089/identity-callback"),
        of("https", 443, "", "https://localhost/identity-callback"),
        of(
            "https",
            9999,
            "/899f3de9-b907-4b7f-9fb7-6925bb5b0a0e",
            "https://localhost:9999/identity-callback?uuid=899f3de9-b907-4b7f-9fb7-6925bb5b0a0e"));
  }

  @ParameterizedTest
  @MethodSource("getRedirectUriWhenTasklistIdentityRootUrlNotProvidedTestData")
  void getRedirectUriWhenTasklistIdentityRootUrlNotProvided(
      String scheme, int port, String path, String expected) {
    // given
    final var req = mock(HttpServletRequest.class);
    when(req.getScheme()).thenReturn(scheme);
    when(req.getServerName()).thenReturn("localhost");
    when(req.getServerPort()).thenReturn(port);
    when(req.getContextPath()).thenReturn(path);

    // when
    final var result = instance.getRedirectURI(req, TasklistURIs.IDENTITY_CALLBACK_URI);

    // then
    assertThat(result).isEqualTo(expected);
  }

  private static Stream<Arguments> getRedirectUriWhenTasklistIdentityRootUrlProvidedTestData() {
    return Stream.of(
        of("https://localhost", "", "https://localhost/identity-callback"),
        of(
            "http://localhost:8123",
            "/test-path",
            "http://localhost:8123/test-path/identity-callback"));
  }

  @ParameterizedTest
  @MethodSource("getRedirectUriWhenTasklistIdentityRootUrlProvidedTestData")
  void getRedirectUriWhenTasklistIdentityRootUrlProvided(
      String identityRedirectRootUrl, String path, String expected) {
    // given
    final var identityProperties = new IdentityProperties();
    identityProperties.setRedirectRootUrl(identityRedirectRootUrl);
    when(tasklistProperties.getIdentity()).thenReturn(identityProperties);

    final var req = mock(HttpServletRequest.class);
    when(req.getContextPath()).thenReturn(path);

    // when
    final var result = instance.getRedirectURI(req, TasklistURIs.IDENTITY_CALLBACK_URI);

    // then
    assertThat(result).isEqualTo(expected);
    verify(req, never()).getScheme();
    verify(req, never()).getServerName();
    verify(req, never()).getServerPort();
  }
}
