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
	"github.com/camunda/zeebe/clients/go/internal/utils"
	"github.com/camunda/zeebe/clients/go/pkg/pb"
	"time"
)

const (
	longPollingOffsetPercent = 0.1
	longPollingMaxDuration   = 10 * time.Second
)

type retryPredicate func(context.Context, error) bool

type Command struct {
	mixin utils.SerializerMixin

	gateway     pb.GatewayClient
	shouldRetry retryPredicate
}

func getLongPollingMillis(ctx context.Context) int64 {
	longPollMillis := int64(-1)

	if deadline, ok := ctx.Deadline(); ok {
		timeout := time.Until(deadline)
		longPollOffset := time.Duration(float64(timeout) * longPollingOffsetPercent)

		if longPollOffset > longPollingMaxDuration {
			longPollOffset = longPollingMaxDuration
		}

		longPollMillis = (timeout - longPollOffset).Milliseconds()
	}

	return longPollMillis
}
