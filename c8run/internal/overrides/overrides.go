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

func SetEnvVars(javaHome string) error {
	envVars := map[string]string{
		"CAMUNDA_OPERATE_CSRFPREVENTIONENABLED":                  "false",
		"CAMUNDA_OPERATE_IMPORTER_READERBACKOFF":                 "1000",
		"CAMUNDA_REST_QUERY_ENABLED":                             "true",
		"CAMUNDA_TASKLIST_CSRFPREVENTIONENABLED":                 "false",
		"ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_DELAY":   "1",
		"ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_SIZE":    "1",
		"ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_INDEX_PREFIX": "zeebe-record",
		"ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL":          "http://localhost:9200",
		"ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME":         "io.camunda.zeebe.exporter.ElasticsearchExporter",
		"ES_JAVA_HOME": javaHome,
		"ES_JAVA_OPTS": "-Xms1g -Xmx1g",
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
	if settings.Username != "" || settings.Password != "" {
		javaOpts = javaOpts + " -Dzeebe.broker.exporters.camundaExporter.args.createSchema=true"
		javaOpts = javaOpts + " -Dzeebe.broker.exporters.camundaExporter.className=io.camunda.exporter.CamundaExporter"
		javaOpts = javaOpts + " -Dcamunda.security.initialization.users[0].name=Demo"
		javaOpts = javaOpts + " -Dcamunda.security.initialization.users[0].email=demo@example.com"
	}
	if settings.Username != "" {
		javaOpts = javaOpts + " -Dcamunda.security.initialization.users[0].username=" + settings.Username
		javaOpts = javaOpts + " -Dcamunda.security.initialization.defaultRoles.admin.users[0]=" + settings.Username
	}
	if settings.Password != "" {
		javaOpts = javaOpts + " -Dcamunda.security.initialization.users[0].password=" + settings.Password
	}
	javaOpts = javaOpts + " -Dcamunda.security.authentication.unprotected-api=true"
	javaOpts = javaOpts + " -Dcamunda.security.authorizations.enabled=false"
	javaOpts = javaOpts + " -Dspring.profiles.active=operate,tasklist,broker,identity,consolidated-auth"
	if err := os.Setenv("CAMUNDA_OPERATE_ZEEBE_RESTADDRESS", protocol+"://localhost:"+strconv.Itoa(settings.Port)); err != nil {
		log.Error().Err(err).Msg("failed to set CAMUNDA_OPERATE_ZEEBE_RESTADDRESS")
	}
	return javaOpts
}
