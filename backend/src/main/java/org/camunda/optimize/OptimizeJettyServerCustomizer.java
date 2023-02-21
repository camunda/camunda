/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.jetty.NotFoundErrorHandler;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.eclipse.jetty.rewrite.handler.HeaderPatternRule;
import org.eclipse.jetty.rewrite.handler.RedirectPatternRule;
import org.eclipse.jetty.rewrite.handler.RewriteHandler;
import org.eclipse.jetty.rewrite.handler.RewriteRegexRule;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.webapp.AbstractConfiguration;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.springframework.boot.web.embedded.jetty.JettyServerCustomizer;
import org.springframework.boot.web.embedded.jetty.JettyServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.google.common.net.HttpHeaders.CONTENT_SECURITY_POLICY;
import static com.google.common.net.HttpHeaders.X_CONTENT_TYPE_OPTIONS;
import static com.google.common.net.HttpHeaders.X_XSS_PROTECTION;
import static org.camunda.optimize.JettyConfig.getResponseHeadersConfiguration;
import static org.camunda.optimize.jetty.OptimizeResourceConstants.REST_API_PATH;

@Component
@RequiredArgsConstructor
public class OptimizeJettyServerCustomizer implements WebServerFactoryCustomizer<JettyServletWebServerFactory> {
  public static final String EXTERNAL_SUB_PATH = "/external";
  private static final String SUB_PATH_PATTERN_TEMPLATE = "^%s(/?)(.*)$";
  private final ConfigurationService configurationService;

  @Override
  public void customize(JettyServletWebServerFactory factory) {
    JettyServerCustomizer jettyServerCustomizer = server -> {
      Handler handler = server.getHandler();
      overwriteJettyErrorHandler(server);

      final HandlerCollection handlerCollection = new HandlerCollection();
      handlerCollection.addHandler(createSecurityHeaderHandlers(configurationService));

      // the external API path rewrite handler is wrapping the app context modifying any external api requests
      // before the appServletContextHandler receives them
      final Handler externalApiRewriteHandler = replacePathSectionRewriteHandler(
        factory,
        handler,
        EXTERNAL_SUB_PATH + REST_API_PATH,
        REST_API_PATH + EXTERNAL_SUB_PATH
      );

      // If running in cloud environment an additional rewrite handler is added to handle requests containing the
      // clusterId as sub-path and effectively stripping of this path element to handle the request as if
      // it was received without that sub-path.
      // This one wraps the external API handler to always strip the clusterId first.
      final String clusterId = configurationService.getAuthConfiguration().getCloudAuthConfiguration().getClusterId();
      if (StringUtils.isNotBlank(clusterId)) {
        final RewriteHandler alternativeApplicationRootPathRewriteHandler =
          createAlternativeApplicationRootPathRewriteHandler(externalApiRewriteHandler, "/" + clusterId);
        handlerCollection.addHandler(alternativeApplicationRootPathRewriteHandler);
      } else {
        // otherwise just the external path rewrite handler is added
        handlerCollection.addHandler(externalApiRewriteHandler);
      }

      handlerCollection.addHandler(handler);
      server.setHandler(handlerCollection);
    };
    factory.addServerCustomizers(jettyServerCustomizer);
  }

  private void overwriteJettyErrorHandler(Server server) {
    // https://github.com/spring-projects/spring-boot/issues/19520#issuecomment-574213270
    getWebAppContext(server).ifPresent(context -> {
      Configuration[] configurations = context.getConfigurations();
      Configuration[] modifiedConfigurations = new Configuration[configurations.length + 1];
      System.arraycopy(configurations, 0, modifiedConfigurations, 0, configurations.length);
      modifiedConfigurations[configurations.length] = new CustomErrorHandlerConfiguration();
      context.setConfigurations(modifiedConfigurations);
    });
  }

  private Optional<WebAppContext> getWebAppContext(final Server server) {
    WebAppContext context = null;
    HandlerWrapper handlerWrapper = server;
    while (context == null && handlerWrapper.getHandler() instanceof HandlerWrapper) {
      handlerWrapper = (HandlerWrapper) handlerWrapper.getHandler();
      if (handlerWrapper instanceof WebAppContext) {
        context = (WebAppContext) handlerWrapper;
      }
    }
    return Optional.ofNullable(context);
  }

  private RewriteHandler createSecurityHeaderHandlers(final ConfigurationService configurationService) {
    final RewriteHandler rewriteHandler = new RewriteHandler();
    HeaderPatternRule xssProtection =
      new HeaderPatternRule("*", X_XSS_PROTECTION, getResponseHeadersConfiguration(configurationService).getXsssProtection());
    rewriteHandler.addRule(xssProtection);

    if (Boolean.TRUE.equals(getResponseHeadersConfiguration(configurationService).getXContentTypeOptions())) {
      final HeaderPatternRule xContentTypeOptions =
        new HeaderPatternRule("*", X_CONTENT_TYPE_OPTIONS, "nosniff");
      rewriteHandler.addRule(xContentTypeOptions);
    }

    final HeaderPatternRule contentSecurityPolicy =
      new HeaderPatternRule(
        "*",
        CONTENT_SECURITY_POLICY,
        getResponseHeadersConfiguration(configurationService).getContentSecurityPolicy()
      );
    rewriteHandler.addRule(contentSecurityPolicy);
    return rewriteHandler;
  }

  private Handler replacePathSectionRewriteHandler(final JettyServletWebServerFactory factory,
                                                   final Handler handler,
                                                   final String originalPath,
                                                   final String replacementPath) {
    final RewriteHandler rewriteHandler = new RewriteHandler();
    rewriteHandler.setRewriteRequestURI(true);
    rewriteHandler.setRewritePathInfo(true);
    final RewriteRegexRule alternativeRootPathEraserRegexRule = new RewriteRegexRule(
      String.format(SUB_PATH_PATTERN_TEMPLATE, factory.getContextPath() + originalPath),
      factory.getContextPath() + replacementPath + "/$2"
    );
    rewriteHandler.addRule(alternativeRootPathEraserRegexRule);
    rewriteHandler.setHandler(handler);
    return rewriteHandler;
  }

  private RewriteHandler createAlternativeApplicationRootPathRewriteHandler(final Handler handler,
                                                                            final String absoluteSubPath) {
    final RewriteHandler rewriteHandler = new RewriteHandler();
    rewriteHandler.setRewriteRequestURI(true);
    rewriteHandler.setRewritePathInfo(true);
    // frontend resources rely on relative paths on the root, thus we need to make sure that alternative app root
    // locations are always followed by a slash
    final RedirectPatternRule trailingSlashRedirectPatternRule = new RedirectPatternRule(
      absoluteSubPath, absoluteSubPath + "/"
    );
    rewriteHandler.addRule(trailingSlashRedirectPatternRule);
    // for resources "below" the alternative root we need to remove the sub-path to route the request to the actual
    // application context being based on "/"
    final RewriteRegexRule alternativeRootPathEraserRegexRule = new RewriteRegexRule(
      String.format(SUB_PATH_PATTERN_TEMPLATE, absoluteSubPath), "/$2"
    );
    rewriteHandler.addRule(alternativeRootPathEraserRegexRule);
    rewriteHandler.setHandler(handler);
    return rewriteHandler;
  }

  private static class CustomErrorHandlerConfiguration extends AbstractConfiguration {
    @Override
    public void configure(WebAppContext context) throws Exception {
      context.setErrorHandler(new NotFoundErrorHandler());
    }
  }

}
