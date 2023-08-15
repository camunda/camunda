/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package commands

import (
	"context"
	"github.com/camunda/zeebe/clients/go/v8/pkg/pb"
)

type DeleteResourceCommandStep1 interface {
	ResourceKey(int64) DispatchDeleteResourceCommand
}

type DispatchDeleteResourceCommand interface {
	Send(context.Context) (*pb.DeleteResourceResponse, error)
}

type DeleteResourceCommand struct {
	Command
	request pb.DeleteResourceRequest
}

func (cmd *DeleteResourceCommand) Send(ctx context.Context) (*pb.DeleteResourceResponse, error) {
	response, err := cmd.gateway.DeleteResource(ctx, &cmd.request)
	if cmd.shouldRetry(ctx, err) {
		return cmd.Send(ctx)
	}

	return response, err
}

func (cmd *DeleteResourceCommand) ResourceKey(key int64) DispatchDeleteResourceCommand {
	cmd.request = pb.DeleteResourceRequest{ResourceKey: key}
	return cmd
}

func NewDeleteResourceCommand(gateway pb.GatewayClient, pred retryPredicate) DeleteResourceCommandStep1 {
	return &DeleteResourceCommand{
		Command: Command{
			gateway:     gateway,
			shouldRetry: pred,
		},
	}
}
