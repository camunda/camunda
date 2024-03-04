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
package io.camunda.operate.webapp.security.ldap;

import static io.camunda.operate.OperateProfileService.LDAP_AUTH_PROFILE;
import static io.camunda.operate.webapp.security.Permission.READ;
import static io.camunda.operate.webapp.security.Permission.WRITE;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.rest.exception.UserNotFoundException;
import io.camunda.operate.webapp.security.AbstractUserService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.ldap.userdetails.LdapUserDetails;
import org.springframework.stereotype.Component;

@Component
@Profile(LDAP_AUTH_PROFILE)
public class LDAPUserService extends AbstractUserService<Authentication> {

  private static final Logger logger = LoggerFactory.getLogger(LDAPUserService.class);

  @Autowired private LdapTemplate ldapTemplate;

  @Autowired private OperateProperties operateProperties;

  private Map<String, UserDto> ldapDnToUser = new ConcurrentHashMap<>();

  @Override
  public UserDto createUserDtoFrom(final Authentication authentication) {
    final LdapUserDetails userDetails = (LdapUserDetails) authentication.getPrincipal();
    final String dn = userDetails.getDn();
    if (!ldapDnToUser.containsKey(dn)) {
      logger.info(String.format("Do a LDAP Lookup for user DN: %s)", dn));
      try {
        ldapDnToUser.put(dn, ldapTemplate.lookup(dn, new LdapUserAttributesMapper()));
      } catch (Exception ex) {
        throw new UserNotFoundException(String.format("Couldn't find user for dn %s", dn));
      }
    }
    return ldapDnToUser.get(dn);
  }

  public void cleanUp(Authentication authentication) {
    final LdapUserDetails userDetails = (LdapUserDetails) authentication.getPrincipal();
    final String dn = userDetails.getDn();
    ldapDnToUser.remove(dn);
  }

  @Override
  public String getUserToken(final Authentication authentication) {
    throw new UnsupportedOperationException("Get token is not supported for LDAP authentication");
  }

  private class LdapUserAttributesMapper implements AttributesMapper<UserDto> {

    private LdapUserAttributesMapper() {}

    public UserDto mapFromAttributes(Attributes attrs) throws NamingException {
      final UserDto userDto = new UserDto().setCanLogout(true);
      final Attribute userIdAttr = attrs.get(operateProperties.getLdap().getUserIdAttrName());
      if (userIdAttr != null) {
        userDto.setUserId((String) userIdAttr.get());
      }
      final Attribute displayNameAttr =
          attrs.get(operateProperties.getLdap().getDisplayNameAttrName());
      if (displayNameAttr != null) {
        userDto.setDisplayName((String) displayNameAttr.get());
      }
      // for now can do all TODO: how to retrieve LDAP Roles/Permissions ?
      userDto.setPermissions(List.of(READ, WRITE));
      return userDto;
    }
  }
}
