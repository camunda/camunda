package io.camunda.zeebe.spring.client.configuration;

import static org.springframework.util.StringUtils.hasText;

import io.camunda.common.auth.*;
import io.camunda.common.auth.identity.IdentityConfig;
import io.camunda.common.auth.identity.IdentityContainer;
import io.camunda.common.exception.SdkException;
import io.camunda.common.json.JsonMapper;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.zeebe.spring.client.properties.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@EnableConfigurationProperties({
  CommonConfigurationProperties.class,
  ZeebeSelfManagedProperties.class
})
public class CommonClientConfiguration {

  @Autowired(required = false)
  CommonConfigurationProperties commonConfigurationProperties;

  @Autowired(required = false)
  ZeebeClientConfigurationProperties zeebeClientConfigurationProperties;

  @Autowired(required = false)
  ConsoleClientConfigurationProperties consoleClientConfigurationProperties;

  @Autowired(required = false)
  OptimizeClientConfigurationProperties optimizeClientConfigurationProperties;

  @Autowired(required = false)
  TasklistClientConfigurationProperties tasklistClientConfigurationProperties;

  @Autowired(required = false)
  OperateClientConfigurationProperties operateClientConfigurationProperties;

  @Autowired(required = false)
  ZeebeSelfManagedProperties zeebeSelfManagedProperties;

  @Autowired(required = false)
  private IdentityConfiguration identityConfigurationFromProperties;

  @Bean
  public Authentication authentication(JsonMapper jsonMapper) {

    // TODO: Refactor
    if (zeebeClientConfigurationProperties != null) {
      // check if Zeebe has clusterId provided, then must be SaaS
      if (zeebeClientConfigurationProperties.getCloud().getClusterId() != null) {
        return SaaSAuthentication.builder()
            .withJwtConfig(configureJwtConfig())
            .withJsonMapper(jsonMapper)
            .build();
      } else if (zeebeClientConfigurationProperties.getBroker().getGatewayAddress() != null
          || zeebeSelfManagedProperties.getGatewayAddress() != null) {
        // figure out if Self-Managed JWT or Self-Managed Basic
        // Operate Client props take first priority
        if (operateClientConfigurationProperties != null) {
          if (hasText(operateClientConfigurationProperties.getKeycloakUrl())
              || hasText(operateClientConfigurationProperties.getKeycloakTokenUrl())) {
            JwtConfig jwtConfig = configureJwtConfig();
            IdentityConfig identityConfig = configureIdentities(jwtConfig);
            return SelfManagedAuthentication.builder()
                .withJwtConfig(jwtConfig)
                .withIdentityConfig(identityConfig)
                .build();
          } else if (operateClientConfigurationProperties.getUsername() != null
              && operateClientConfigurationProperties.getPassword() != null) {
            SimpleConfig simpleConfig = new SimpleConfig();
            SimpleCredential simpleCredential =
                new SimpleCredential(
                    operateClientConfigurationProperties.getUsername(),
                    operateClientConfigurationProperties.getPassword());
            simpleConfig.addProduct(Product.OPERATE, simpleCredential);
            return SimpleAuthentication.builder()
                .withSimpleConfig(simpleConfig)
                .withSimpleUrl(operateClientConfigurationProperties.getUrl())
                .build();
          }
        }

        // Identity props take second priority
        if (identityConfigurationFromProperties != null) {
          if (hasText(identityConfigurationFromProperties.getClientId())) {
            JwtConfig jwtConfig = configureJwtConfig();
            IdentityConfig identityConfig = configureIdentities(jwtConfig);
            return SelfManagedAuthentication.builder()
                .withJwtConfig(jwtConfig)
                .withIdentityConfig(identityConfig)
                .build();
          }
        }

        // Fallback to common props
        if (commonConfigurationProperties != null) {
          if (commonConfigurationProperties.getKeycloak().getUrl() != null) {
            JwtConfig jwtConfig = configureJwtConfig();
            IdentityConfig identityConfig = configureIdentities(jwtConfig);
            return SelfManagedAuthentication.builder()
                .withJwtConfig(jwtConfig)
                .withIdentityConfig(identityConfig)
                .build();
          } else if (commonConfigurationProperties.getKeycloak().getTokenUrl() != null) {
            JwtConfig jwtConfig = configureJwtConfig();
            IdentityConfig identityConfig = configureIdentities(jwtConfig);
            return SelfManagedAuthentication.builder()
                .withJwtConfig(jwtConfig)
                .withIdentityConfig(identityConfig)
                .build();
          } else if (commonConfigurationProperties.getUsername() != null
              && commonConfigurationProperties.getPassword() != null) {
            SimpleConfig simpleConfig = new SimpleConfig();
            SimpleCredential simpleCredential =
                new SimpleCredential(
                    commonConfigurationProperties.getUsername(),
                    commonConfigurationProperties.getPassword());
            simpleConfig.addProduct(Product.OPERATE, simpleCredential);
            return SimpleAuthentication.builder()
                .withSimpleConfig(simpleConfig)
                .withSimpleUrl(commonConfigurationProperties.getUrl())
                .build();
          }
        }
      }
    }
    return new DefaultNoopAuthentication();
  }

  private JwtConfig configureJwtConfig() {
    JwtConfig jwtConfig = new JwtConfig();
    // ZEEBE
    if (zeebeClientConfigurationProperties.getCloud().getClientId() != null
        && zeebeClientConfigurationProperties.getCloud().getClientSecret() != null) {
      jwtConfig.addProduct(
          Product.ZEEBE,
          new JwtCredential(
              zeebeClientConfigurationProperties.getCloud().getClientId(),
              zeebeClientConfigurationProperties.getCloud().getClientSecret(),
              zeebeClientConfigurationProperties.getCloud().getAudience(),
              zeebeClientConfigurationProperties.getCloud().getAuthUrl()));
    } else if (zeebeSelfManagedProperties.getClientId() != null
        && zeebeSelfManagedProperties.getClientSecret() != null) {
      jwtConfig.addProduct(
          Product.ZEEBE,
          new JwtCredential(
              zeebeSelfManagedProperties.getClientId(),
              zeebeSelfManagedProperties.getClientSecret(),
              zeebeSelfManagedProperties.getAudience(),
              zeebeSelfManagedProperties.getAuthServer()));
    } else if (commonConfigurationProperties.getClientId() != null
        && commonConfigurationProperties.getClientSecret() != null) {
      jwtConfig.addProduct(
          Product.ZEEBE,
          new JwtCredential(
              commonConfigurationProperties.getClientId(),
              commonConfigurationProperties.getClientSecret(),
              zeebeClientConfigurationProperties.getCloud().getAudience(),
              zeebeClientConfigurationProperties.getCloud().getAuthUrl()));
    }

    // OPERATE
    String operateAuthUrl = zeebeClientConfigurationProperties.getCloud().getAuthUrl();
    String operateAudience = "operate.camunda.io";
    if (operateClientConfigurationProperties != null) {
      // override authUrl
      if (operateClientConfigurationProperties.getAuthUrl() != null) {
        operateAuthUrl = operateClientConfigurationProperties.getAuthUrl();
      } else if (hasText(operateClientConfigurationProperties.getKeycloakTokenUrl())) {
        operateAuthUrl = operateClientConfigurationProperties.getKeycloakTokenUrl();
      } else if (hasText(operateClientConfigurationProperties.getKeycloakUrl())
          && hasText(operateClientConfigurationProperties.getKeycloakRealm())) {
        operateAuthUrl =
            operateClientConfigurationProperties.getKeycloakUrl()
                + "/auth/realms/"
                + operateClientConfigurationProperties.getKeycloakRealm();
      }

      if (operateClientConfigurationProperties.getBaseUrl() != null) {
        operateAudience = operateClientConfigurationProperties.getBaseUrl();
      }

      if (operateClientConfigurationProperties.getClientId() != null
          && operateClientConfigurationProperties.getClientSecret() != null) {
        jwtConfig.addProduct(
            Product.OPERATE,
            new JwtCredential(
                operateClientConfigurationProperties.getClientId(),
                operateClientConfigurationProperties.getClientSecret(),
                operateAudience,
                operateAuthUrl));
      } else if (identityConfigurationFromProperties != null
          && hasText(identityConfigurationFromProperties.getClientId())
          && hasText(identityConfigurationFromProperties.getClientSecret())) {
        jwtConfig.addProduct(
            Product.OPERATE,
            new JwtCredential(
                identityConfigurationFromProperties.getClientId(),
                identityConfigurationFromProperties.getClientSecret(),
                identityConfigurationFromProperties.getAudience(),
                identityConfigurationFromProperties.getIssuerBackendUrl()));
      } else if (commonConfigurationProperties.getClientId() != null
          && commonConfigurationProperties.getClientSecret() != null) {
        jwtConfig.addProduct(
            Product.OPERATE,
            new JwtCredential(
                commonConfigurationProperties.getClientId(),
                commonConfigurationProperties.getClientSecret(),
                operateAudience,
                operateAuthUrl));
      } else if (zeebeClientConfigurationProperties.getCloud().getClientId() != null
          && zeebeClientConfigurationProperties.getCloud().getClientSecret() != null) {
        jwtConfig.addProduct(
            Product.OPERATE,
            new JwtCredential(
                zeebeClientConfigurationProperties.getCloud().getClientId(),
                zeebeClientConfigurationProperties.getCloud().getClientSecret(),
                operateAudience,
                operateAuthUrl));
      } else if (zeebeSelfManagedProperties.getClientId() != null
          && zeebeSelfManagedProperties.getClientSecret() != null) {
        jwtConfig.addProduct(
            Product.OPERATE,
            new JwtCredential(
                zeebeSelfManagedProperties.getClientId(),
                zeebeSelfManagedProperties.getClientSecret(),
                operateAudience,
                operateAuthUrl));
      } else {
        throw new SdkException("Unable to determine OPERATE credentials");
      }
    }
    return jwtConfig;
  }

  private IdentityConfig configureIdentities(JwtConfig jwtConfig) {
    IdentityConfig identityConfig = new IdentityConfig();

    // TODO: Should we handle Zeebe with Identity SDK?
    // OPERATE
    if (operateClientConfigurationProperties != null) {
      IdentityContainer operateIdentityContainer = configureOperateIdentityContainer(jwtConfig);
      identityConfig.addProduct(Product.OPERATE, operateIdentityContainer);
    }
    return identityConfig;
  }

  /**
   * Identity properties supplied by the user are optional, so if they don't exist, we have to
   * backfill them from somewhere like the OperateClientConfigurationProperties.
   *
   * @param jwtConfig
   * @return
   */
  private IdentityContainer configureOperateIdentityContainer(JwtConfig jwtConfig) {
    String issuer;
    String issuerBackendUrl;
    if (hasText(identityConfigurationFromProperties.getIssuer())) {
      issuer = identityConfigurationFromProperties.getIssuer();
    } else {
      issuer = jwtConfig.getProduct(Product.OPERATE).getAuthUrl();
    }

    if (hasText(identityConfigurationFromProperties.getIssuerBackendUrl())) {
      issuerBackendUrl = identityConfigurationFromProperties.getIssuerBackendUrl();
    } else {
      issuerBackendUrl = jwtConfig.getProduct(Product.OPERATE).getAuthUrl();
    }

    IdentityConfiguration operateIdentityConfiguration =
        new IdentityConfiguration.Builder()
            .withBaseUrl(identityConfigurationFromProperties.getBaseUrl())
            .withIssuer(issuer)
            .withIssuerBackendUrl(issuerBackendUrl)
            .withClientId(jwtConfig.getProduct(Product.OPERATE).getClientId())
            .withClientSecret(jwtConfig.getProduct(Product.OPERATE).getClientSecret())
            .withAudience(jwtConfig.getProduct(Product.OPERATE).getAudience())
            .withType(identityConfigurationFromProperties.getType().name())
            .build();
    Identity operateIdentity = new Identity(operateIdentityConfiguration);
    return new IdentityContainer(operateIdentity, operateIdentityConfiguration);
  }
}
