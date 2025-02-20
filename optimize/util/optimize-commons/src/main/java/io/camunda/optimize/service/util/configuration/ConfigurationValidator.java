/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import static io.camunda.optimize.service.util.configuration.ConfigurationParser.parseConfigFromLocations;
import static io.camunda.optimize.service.util.configuration.ConfigurationUtil.getLocationsAsInputStream;
import static java.util.stream.Collectors.toMap;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ReadContext;
import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import io.camunda.optimize.util.SuppressionConstants;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;

public class ConfigurationValidator {

  public static final String DOC_URL =
      "https://docs.camunda.io/optimize/next/self-managed/optimize-deployment/configuration/system-configuration/";
  private static final String[] DEFAULT_DELETED_CONFIG_LOCATIONS = {"deleted-config.yaml"};
  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(ConfigurationValidator.class);

  private final Map<String, String> deletedConfigKeys;

  public ConfigurationValidator() {
    this(null);
  }

  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  public ConfigurationValidator(final String[] deletedConfigLocations) {
    final List<InputStream> deletedConfigStreams =
        getLocationsAsInputStream(
            deletedConfigLocations == null
                ? DEFAULT_DELETED_CONFIG_LOCATIONS
                : deletedConfigLocations);

    deletedConfigKeys =
        (Map<String, String>)
            parseConfigFromLocations(deletedConfigStreams)
                .map(ReadContext::json)
                .orElse(Collections.emptyMap());
  }

  public void validate(final ConfigurationService configurationService) {
    validateNoDeletedConfigKeysUsed(configurationService.getConfigJsonContext());
    configurationService.getEmailAuthenticationConfiguration().validate();
  }

  public static ConfigurationValidator createValidatorWithoutDeletions() {
    return new ConfigurationValidator(new String[] {});
  }

  private void validateNoDeletedConfigKeysUsed(final ReadContext configJsonContext) {
    final Configuration conf =
        Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS);
    final DocumentContext failsafeConfigurationJsonContext =
        JsonPath.using(conf).parse((Object) configJsonContext.json());

    final Map<String, String> usedDeletionKeysWithNewDocumentationPath =
        deletedConfigKeys.entrySet().stream()
            .filter(
                entry ->
                    Optional.ofNullable(
                            failsafeConfigurationJsonContext.read("$." + entry.getKey()))
                        // in case of array structures we always a list as result, thus we need to
                        // check if it contains actual results
                        .flatMap(
                            object ->
                                object instanceof Collection && ((Collection<?>) object).isEmpty()
                                    ? Optional.empty()
                                    : Optional.of(object))
                        .isPresent())
            // We now set the URL to be the generic docs URL because we don't have consistent
            // versioning across docs
            .peek(keyAndUrl -> keyAndUrl.setValue(DOC_URL))
            .peek(
                keyAndUrl ->
                    LOG.error(
                        "Deleted setting used with key {}, please see the updated documentation {}",
                        keyAndUrl.getKey(),
                        DOC_URL))
            .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

    if (!usedDeletionKeysWithNewDocumentationPath.isEmpty()) {
      throw new OptimizeConfigurationException(
          "Configuration contains deleted entries", usedDeletionKeysWithNewDocumentationPath);
    }
  }
}
