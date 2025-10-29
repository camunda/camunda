/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package overrides

import (
	"fmt"
	"os"
	"strconv"

	"github.com/camunda/camunda/c8run/internal/types"
	"github.com/rs/zerolog/log"
)

func SetEnvVars(javaHome string, shouldStartElasticsearch bool) error {
	envVars := map[string]string{
		"CAMUNDA_OPERATE_CSRFPREVENTIONENABLED":                  "false",
		"CAMUNDA_OPERATE_IMPORTER_READERBACKOFF":                 "1000",
		"CAMUNDA_REST_QUERY_ENABLED":                             "true",
		"CAMUNDA_TASKLIST_CSRFPREVENTIONENABLED":                 "false",
		"ES_JAVA_HOME": javaHome,
		"ES_JAVA_OPTS": "-Xms1g -Xmx1g",
	}
	if shouldStartElasticsearch {
		envVars["ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_DELAY"] = "1"
		envVars["ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_SIZE"] = "1"
		envVars["ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_INDEX_PREFIX"] = "zeebe-record"
		envVars["ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL"] = "http://localhost:9200"
		envVars["ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME"] = "io.camunda.zeebe.exporter.ElasticsearchExporter"
	}

	for key, value := range envVars {
		currentValue := os.Getenv(key)
		if currentValue != "" {
			continue
		}
		if err := os.Setenv(key, value); err != nil {
			return fmt.Errorf("failed to set environment variable %s: %w", key, err)
		}
	}

	return nil
}

func AdjustJavaOpts(javaOpts string, settings types.C8RunSettings) string {
	protocol := "http"
	if settings.HasKeyStore() {
		javaOpts = javaOpts + " -Dserver.ssl.keystore=file:" + settings.Keystore + " -Dserver.ssl.enabled=true" + " -Dserver.ssl.key-password=" + settings.KeystorePassword
		protocol = "https"
	}
	if settings.Port != 8080 {
		javaOpts = javaOpts + " -Dserver.port=" + strconv.Itoa(settings.Port)
	}
	// as demo is set in the default config, we only add the user settings if they differ
	if settings.Username != "demo" {
		javaOpts = javaOpts + " -Dcamunda.security.initialization.users[0].username=" + settings.Username
		javaOpts = javaOpts + " -Dcamunda.security.initialization.users[0].name=" + settings.Username
		javaOpts = javaOpts + " -Dcamunda.security.initialization.users[0].email=" + settings.Username + "@example.com"
		javaOpts = javaOpts + " -Dcamunda.security.initialization.defaultRoles.admin.users[0]=" + settings.Username
	}
	if settings.Password != "demo" {
		javaOpts = javaOpts + " -Dcamunda.security.initialization.users[0].password=" + settings.Password
	}
	if err := os.Setenv("CAMUNDA_OPERATE_ZEEBE_RESTADDRESS", protocol+"://localhost:"+strconv.Itoa(settings.Port)); err != nil {
		log.Error().Err(err).Msg("failed to set CAMUNDA_OPERATE_ZEEBE_RESTADDRESS")
	}
	return javaOpts
}
