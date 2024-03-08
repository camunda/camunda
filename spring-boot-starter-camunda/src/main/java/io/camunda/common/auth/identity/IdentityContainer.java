package io.camunda.common.auth.identity;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.IdentityConfiguration;

public class IdentityContainer {

  Identity identity;
  IdentityConfiguration identityConfiguration;

  public IdentityContainer(Identity identity, IdentityConfiguration identityConfiguration) {
    this.identity = identity;
    this.identityConfiguration = identityConfiguration;
  }

  public Identity getIdentity() {
    return identity;
  }

  public IdentityConfiguration getIdentityConfiguration() {
    return identityConfiguration;
  }
}
