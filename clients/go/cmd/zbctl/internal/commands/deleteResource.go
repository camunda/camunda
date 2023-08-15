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
	"fmt"
	"github.com/camunda/zeebe/clients/go/v8/pkg/pb"
	"github.com/spf13/cobra"
)

type DeleteResourceResponseWrapper struct {
	resp *pb.DeleteResourceResponse
}

func (c DeleteResourceResponseWrapper) human() (string, error) {
	return fmt.Sprint("Deleted resource with key '", resourceKey, "'"), nil
}

func (c DeleteResourceResponseWrapper) json() (string, error) {
	return toJSON(c.resp)
}

var resourceKey int64

var deleteResourceCmd = &cobra.Command{
	Use:     "resource <key>",
	Short:   "Delete resource by key",
	Args:    keyArg(&resourceKey),
	PreRunE: initClient,
	RunE: func(cmd *cobra.Command, args []string) error {
		zbCmd := client.NewDeleteResourceCommand().ResourceKey(resourceKey)
		ctx, cancel := context.WithTimeout(context.Background(), timeoutFlag)
		defer cancel()

		resp, err := zbCmd.Send(ctx)
		if err != nil {
			return err
		}
		err = printOutput(DeleteResourceResponseWrapper{resp})
		return err
	},
}

func init() {
	addOutputFlag(deleteResourceCmd)
	deleteCmd.AddCommand(deleteResourceCmd)
}
