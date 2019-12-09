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
	"github.com/golang/mock/gomock"
	"github.com/zeebe-io/zeebe/clients/go/internal/mock_pb"
	"github.com/zeebe-io/zeebe/clients/go/internal/utils"
	"github.com/zeebe-io/zeebe/clients/go/pkg/pb"
	"testing"
)

func TestThrowErrorCommand(t *testing.T) {
	ctrl := gomock.NewController(t)
	defer ctrl.Finish()

	client := mock_pb.NewMockGatewayClient(ctrl)

	request := &pb.ThrowErrorRequest{
		JobKey:       123,
		ErrorCode:    "someErrorCode",
		ErrorMessage: "someErrorMessage",
	}
	stub := &pb.ThrowErrorResponse{}

	client.EXPECT().ThrowError(gomock.Any(), &utils.RpcTestMsg{Msg: request}).Return(stub, nil)

	command := NewThrowErrorCommand(client, utils.DefaultTestTimeout, func(error) bool { return false })

	response, err := command.JobKey(123).ErrorCode("someErrorCode").ErrorMessage("someErrorMessage").Send()

	if err != nil {
		t.Errorf("Failed to send request")
	}

	if response != stub {
		t.Errorf("Failed to receive response")
	}
}
