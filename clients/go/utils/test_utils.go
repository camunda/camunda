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

package utils

import (
	"fmt"
	"github.com/golang/protobuf/proto"
	"time"
)

const DefaultTestTimeout = 5 * time.Second

// RpcTestMsg implements the gomock.Matcher interface
type RpcTestMsg struct {
	Msg proto.Message
}

func (r *RpcTestMsg) Matches(msg interface{}) bool {
	m, ok := msg.(proto.Message)
	if !ok {
		return false
	}
	return proto.Equal(m, r.Msg)
}

func (r *RpcTestMsg) String() string {
	return fmt.Sprintf("is %s", r.Msg)
}
