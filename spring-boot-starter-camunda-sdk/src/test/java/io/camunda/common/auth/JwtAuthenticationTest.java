/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.common.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.platform.commons.util.ReflectionUtils.tryToReadFieldValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.common.auth.JwtAuthentication.JwtToken;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationTest {

  JwtAuthentication jwtAuthentication;

  @Mock JwtConfig jwtConfig;

  @Mock Supplier<JwtToken> jwtTokenSupplier;

  @BeforeEach
  void setUp() {
    jwtAuthentication =
        new JwtAuthentication(jwtConfig) {
          @Override
          protected JwtToken generateToken(final Product product, final JwtCredential credential) {
            return jwtTokenSupplier.get();
          }
        };
  }

  @Test
  void shouldGenerateNewTokenIfTokenExpired() throws Exception {
    // given
    final JwtToken currentToken = new JwtToken("expired", LocalDateTime.now().minusSeconds(1));
    setCurrentToken(currentToken);

    final JwtToken newToken = new JwtToken("new", LocalDateTime.now().plusHours(1));
    when(jwtTokenSupplier.get()).thenReturn(newToken);

    // when
    final Entry<String, String> tokenHeader = jwtAuthentication.getTokenHeader(Product.ZEEBE);

    // then
    assertEquals("Authorization", tokenHeader.getKey());
    assertEquals("Bearer new", tokenHeader.getValue());
  }

  @Test
  void shouldReturnCurrentTokenIfTokenIsValid() throws Exception {
    // given
    final JwtToken currentToken = new JwtToken("valid", LocalDateTime.now().plusSeconds(5));
    setCurrentToken(currentToken);

    // when
    final Entry<String, String> tokenHeader = jwtAuthentication.getTokenHeader(Product.ZEEBE);

    // then
    assertEquals("Authorization", tokenHeader.getKey());
    assertEquals("Bearer valid", tokenHeader.getValue());

    verifyNoInteractions(jwtTokenSupplier);
  }

  private void setCurrentToken(final JwtToken validToken) throws Exception {
    final Map<Product, JwtToken> tokens = getTokens();
    tokens.put(Product.ZEEBE, validToken);
  }

  @SuppressWarnings("unchecked")
  private Map<Product, JwtToken> getTokens() throws Exception {
    return (Map<Product, JwtToken>)
        tryToReadFieldValue(JwtAuthentication.class, "tokens", jwtAuthentication).get();
  }
}
