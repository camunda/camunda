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
//

package commands

import (
	"context"
	"github.com/camunda/zeebe/clients/go/v8/internal/mock_pb"
	"github.com/camunda/zeebe/clients/go/v8/internal/utils"
	"github.com/camunda/zeebe/clients/go/v8/pkg/pb"
	"github.com/golang/mock/gomock"
	"testing"
)

func TestFailJobCommand(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.FailJobRequest{
		JobKey:  123,
		Retries: 12,
	}
	stub := &pb.FailJobResponse{}

	client.EXPECT().FailJob(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewFailJobCommand(client, func(context.Context, error) bool { return false })

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := command.JobKey(123).Retries(12).Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}

func TestFailJobCommand_ErrorMessage(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	errorMessage := "something went wrong"

	request := &pb.FailJobRequest{
		JobKey:       123,
		Retries:      12,
		ErrorMessage: errorMessage,
	}
	stub := &pb.FailJobResponse{}

	client.EXPECT().FailJob(gomock.Any(), &utils.RPCTestMsg{Msg: request}).Return(stub, nil)

	command := NewFailJobCommand(client, func(context.Context, error) bool { return false })

	ctx, cancel := context.WithTimeout(context.Background(), utils.DefaultTestTimeout)
	defer cancel()

	response, err := command.JobKey(123).Retries(12).ErrorMessage(errorMessage).Send(ctx)

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}
