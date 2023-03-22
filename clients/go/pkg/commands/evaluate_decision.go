// Copyright © 2018 Camunda Services GmbH (info@camunda.com)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package commands

import (
	"context"
	"fmt"
	"github.com/camunda/zeebe/clients/go/v8/internal/utils"
	"github.com/camunda/zeebe/clients/go/v8/pkg/pb"
)

type DispatchEvaluateDecisionCommand interface {
	Send(context.Context) (*pb.EvaluateDecisionResponse, error)
}

type EvaluateDecisionCommandStep1 interface {
	DecisionId(string) EvaluateDecisionCommandStep2
	DecisionKey(int64) EvaluateDecisionCommandStep2
}

type EvaluateDecisionCommandStep2 interface {
	DispatchEvaluateDecisionCommand

	// Expected to be valid JSON string
	VariablesFromString(string) (EvaluateDecisionCommandStep2, error)

	// Expected to construct a valid JSON string
	VariablesFromStringer(fmt.Stringer) (EvaluateDecisionCommandStep2, error)

	// Expected that object is JSON serializable
	VariablesFromObject(interface{}) (EvaluateDecisionCommandStep2, error)
	VariablesFromObjectIgnoreOmitempty(interface{}) (EvaluateDecisionCommandStep2, error)
	VariablesFromMap(map[string]interface{}) (EvaluateDecisionCommandStep2, error)
}

type EvaluateDecisionCommand struct {
	Command
	request pb.EvaluateDecisionRequest
}

func (cmd *EvaluateDecisionCommand) VariablesFromString(variables string) (EvaluateDecisionCommandStep2, error) {
	err := cmd.mixin.Validate("variables", variables)
	if err != nil {
		return nil, err
	}

	cmd.request.Variables = variables
	return cmd, nil
}

func (cmd *EvaluateDecisionCommand) VariablesFromStringer(variables fmt.Stringer) (EvaluateDecisionCommandStep2, error) {
	return cmd.VariablesFromString(variables.String())
}

func (cmd *EvaluateDecisionCommand) VariablesFromObject(variables interface{}) (EvaluateDecisionCommandStep2, error) {
	value, err := cmd.mixin.AsJSON("variables", variables, false)
	if err != nil {
		return nil, err
	}

	cmd.request.Variables = value
	return cmd, err
}

func (cmd *EvaluateDecisionCommand) VariablesFromObjectIgnoreOmitempty(variables interface{}) (EvaluateDecisionCommandStep2, error) {
	value, err := cmd.mixin.AsJSON("variables", variables, true)
	if err != nil {
		return nil, err
	}

	cmd.request.Variables = value
	return cmd, err
}

func (cmd *EvaluateDecisionCommand) VariablesFromMap(variables map[string]interface{}) (EvaluateDecisionCommandStep2, error) {
	return cmd.VariablesFromObject(variables)
}

func (cmd *EvaluateDecisionCommand) DecisionKey(key int64) EvaluateDecisionCommandStep2 {
	cmd.request.DecisionKey = key
	return cmd
}

//nolint:revive
func (cmd *EvaluateDecisionCommand) DecisionId(id string) EvaluateDecisionCommandStep2 {
	cmd.request.DecisionId = id
	return cmd
}

func (cmd *EvaluateDecisionCommand) Send(ctx context.Context) (*pb.EvaluateDecisionResponse, error) {
	response, err := cmd.gateway.EvaluateDecision(ctx, &cmd.request)
	if cmd.shouldRetry(ctx, err) {
		return cmd.Send(ctx)
	}

	return response, err
}

func NewEvaluateDecisionCommand(gateway pb.GatewayClient, pred retryPredicate) EvaluateDecisionCommandStep1 {
	return &EvaluateDecisionCommand{
		Command: Command{
			mixin:       utils.NewJSONStringSerializer(),
			gateway:     gateway,
			shouldRetry: pred,
		},
	}
}
