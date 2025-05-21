/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package health

import (
	"context"
	"errors"
	"time"

	"github.com/rs/zerolog/log"
)

func QueryElasticsearch(ctx context.Context, name string, retries int, url string) error {
	if isRunning(ctx, name, url, retries, 10*time.Second) {
		log.Info().Str("name", name).Msg("Started successfully")
	} else {
		log.Error().Str("name", name).Msg("Not Started")
		return errors.New("Elasticsearch did not start")
	}
	return nil
}
