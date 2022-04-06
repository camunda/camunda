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

package worker

import (
	"github.com/camunda/zeebe/clients/go/v8/internal/mock_pb"
	"github.com/camunda/zeebe/clients/go/v8/pkg/entities"
	"github.com/camunda/zeebe/clients/go/v8/pkg/pb"
	"github.com/golang/mock/gomock"
	"github.com/stretchr/testify/assert"
	"testing"
	"time"
)

const testDuration = 12 * time.Minute
const testDurationMs = int64(testDuration / time.Millisecond)

func TestJobWorkerBuilder_JobType(t *testing.T) {
	builder := JobWorkerBuilder{request: &pb.ActivateJobsRequest{}}
	builder.JobType("foo")
	assert.Equal(t, "foo", builder.request.Type)
}

func TestJobWorkerBuilder_Handler(t *testing.T) {
	builder := JobWorkerBuilder{}
	builder.Handler(func(JobClient, entities.Job) {})
	assert.NotNil(t, builder.handler)
}

func TestJobWorkerBuilder_Name(t *testing.T) {
	builder := JobWorkerBuilder{request: &pb.ActivateJobsRequest{}}
	builder.Name("foo")
	assert.Equal(t, "foo", builder.request.Worker)
}

func TestJobWorkerBuilder_Timeout(t *testing.T) {
	builder := JobWorkerBuilder{request: &pb.ActivateJobsRequest{}}
	builder.Timeout(testDuration)
	assert.Equal(t, testDurationMs, builder.request.Timeout)
}

func TestJobWorkerBuilder_MaxJobsActive(t *testing.T) {
	builder := JobWorkerBuilder{}
	builder.MaxJobsActive(123)
	assert.Equal(t, 123, builder.maxJobsActive)

	// should ignore invalid buffer size
	builder.MaxJobsActive(0)
	assert.Equal(t, 123, builder.maxJobsActive)
}

func TestJobWorkerBuilder_Concurrency(t *testing.T) {
	builder := JobWorkerBuilder{}
	builder.Concurrency(123)
	assert.Equal(t, 123, builder.concurrency)

	// should ignore invalid concurrency
	builder.Concurrency(0)
	assert.Equal(t, 123, builder.concurrency)
}

func TestJobWorkerBuilder_PollInterval(t *testing.T) {
	builder := JobWorkerBuilder{}
	builder.PollInterval(testDuration)
	assert.Equal(t, testDuration, builder.pollInterval)
}

func TestJobWorkerBuilder_PollThreshold(t *testing.T) {
	builder := JobWorkerBuilder{}
	builder.PollThreshold(0.12)
	assert.Equal(t, 0.12, builder.pollThreshold)

	// should ignore invalid poll threshold
	builder.PollThreshold(0)
	assert.Equal(t, 0.12, builder.pollThreshold)
}

func TestJobWorkerBuilder_FetchVariables(t *testing.T) {
	fetchVariables := []string{"foo", "bar", "baz"}

	builder := JobWorkerBuilder{request: &pb.ActivateJobsRequest{}}
	builder.FetchVariables(fetchVariables...)
	assert.Equal(t, fetchVariables, builder.request.FetchVariable)
}

func TestJobWorkerBuilder_Metrics(t *testing.T) {
	builder := JobWorkerBuilder{}
	workerMetrics := mock_pb.NewMockJobWorkerMetrics(gomock.NewController(t))
	builder.Metrics(workerMetrics)

	assert.Equal(t, workerMetrics, builder.metrics)
}
