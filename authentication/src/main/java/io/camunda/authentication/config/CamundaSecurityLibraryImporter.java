/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.authentication.config.spi.SecurityPathAdapter;
import io.camunda.authentication.config.spi.WebAppProviderAdapter;
import io.camunda.security.core.port.out.SecurityPathPort;
import io.camunda.security.spring.CamundaSecurityAutoConfiguration;
import io.camunda.security.spring.spi.WebAppProviderPort;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Always-loaded entry point that pulls the Camunda Security Library (CSL) auto-configuration into
 * the host context plus the minimal host SPI beans CSL's {@code BaseSecurityConfiguration} requires
 * at boot. No profile gate — so CSL's unprotected-paths chain and catch-all 404 chain light up
 * under both {@code !pt-security} and {@code pt-security}.
 *
 * <p>Previously the {@code @ImportAutoConfiguration} directive lived on {@link WebSecurityConfig},
 * which is profile-gated to {@code consolidated-auth & !pt-security}. Under {@code pt-security}
 * that meant CSL never loaded and we re-implemented its unprotected-paths chain plus a {@link
 * org.springframework.security.web.savedrequest.NullRequestCache} workaround inside the PT
 * registrar. Lifting the import out lets CSL's chains co-exist with the PT chains.
 *
 * <p>The {@link SecurityPathPort} and {@link WebAppProviderPort} beans are stateless adapters with
 * no OC-runtime dependencies, so they live here alongside the import. The other SPI beans ({@code
 * AdminUserPresencePort}, {@code AuthorizationRepositoryPort}, {@code ResourcePermissionPort})
 * carry OC-specific wiring and stay profile-gated on {@link WebSecurityConfig}; CSL chains that
 * depend on them (OidcWebapp, BasicAuth) back off under {@code pt-security} where the PT chains
 * handle those concerns instead.
 *
 * <p>{@code @ImportAutoConfiguration} (vs plain {@code @Import}) is intentional — see the rationale
 * on {@link WebSecurityConfig}: CSL's {@code @ConditionalOnBean} /
 * {@code @ConditionalOnMissingBean} evaluations are unreliable when CSL is pulled in via
 * {@code @Import} because the bean graph is still partial. Auto-configuration import shifts CSL
 * into the deferred phase so its conditions see the full graph.
 */
@Configuration
@ImportAutoConfiguration(CamundaSecurityAutoConfiguration.class)
public class CamundaSecurityLibraryImporter {

  @Bean
  public SecurityPathPort securityPathPort() {
    return new SecurityPathAdapter();
  }

  @Bean
  public WebAppProviderPort webAppProvider() {
    return new WebAppProviderAdapter();
  }
}
