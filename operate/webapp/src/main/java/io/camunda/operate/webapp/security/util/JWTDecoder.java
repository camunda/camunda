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
package io.camunda.operate.webapp.security.util;

import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.impl.JWTParser;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.Header;
import com.auth0.jwt.interfaces.Payload;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * This class is based on com.auth0.jwt.JWTDecoder As we don't have access to it (default package
 * accessible)
 */
public class JWTDecoder implements DecodedJWT, Serializable {
  private static final long serialVersionUID = 1873362438023312895L;
  private final String[] parts;
  private final Header header;
  private final Payload payload;

  public JWTDecoder(String jwt) throws JWTDecodeException {
    this(new JWTParser(), jwt);
  }

  public JWTDecoder(JWTParser converter, String jwt) throws JWTDecodeException {
    this.parts = TokenUtils.splitToken(jwt);

    String headerJson;
    String payloadJson;
    try {
      headerJson = new String(Base64.getUrlDecoder().decode(this.parts[0]), StandardCharsets.UTF_8);
      payloadJson =
          new String(Base64.getUrlDecoder().decode(this.parts[1]), StandardCharsets.UTF_8);
    } catch (NullPointerException var6) {
      throw new JWTDecodeException("The UTF-8 Charset isn't initialized.", var6);
    } catch (IllegalArgumentException var7) {
      throw new JWTDecodeException("The input is not a valid base 64 encoded string.", var7);
    }

    this.header = converter.parseHeader(headerJson);
    this.payload = converter.parsePayload(payloadJson);
  }

  public Payload getPayloadObject() {
    return payload;
  }

  public String getAlgorithm() {
    return this.header.getAlgorithm();
  }

  public String getType() {
    return this.header.getType();
  }

  public String getContentType() {
    return this.header.getContentType();
  }

  public String getKeyId() {
    return this.header.getKeyId();
  }

  public Claim getHeaderClaim(String name) {
    return this.header.getHeaderClaim(name);
  }

  public String getIssuer() {
    return this.payload.getIssuer();
  }

  public String getSubject() {
    return this.payload.getSubject();
  }

  public List<String> getAudience() {
    return this.payload.getAudience();
  }

  public Date getExpiresAt() {
    return this.payload.getExpiresAt();
  }

  public Date getNotBefore() {
    return this.payload.getNotBefore();
  }

  public Date getIssuedAt() {
    return this.payload.getIssuedAt();
  }

  public String getId() {
    return this.payload.getId();
  }

  public Claim getClaim(String name) {
    return this.payload.getClaim(name);
  }

  public Map<String, Claim> getClaims() {
    return this.payload.getClaims();
  }

  public String getToken() {
    return String.format("%s.%s.%s", this.parts[0], this.parts[1], this.parts[2]);
  }

  public String getHeader() {
    return this.parts[0];
  }

  public String getPayload() {
    return this.parts[1];
  }

  public String getSignature() {
    return this.parts[2];
  }

  abstract static class TokenUtils {
    TokenUtils() {}

    static String[] splitToken(String token) throws JWTDecodeException {
      String[] parts = token.split("\\.");
      if (parts.length == 2 && token.endsWith(".")) {
        parts = new String[] {parts[0], parts[1], ""};
      }

      if (parts.length != 3) {
        throw new JWTDecodeException(
            String.format("The token was expected to have 3 parts, but got %s.", parts.length));
      } else {
        return parts;
      }
    }
  }
}
