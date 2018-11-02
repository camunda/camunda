package worker

import (
	"github.com/stretchr/testify/assert"
	"github.com/zeebe-io/zeebe/clients/go/entities"
	"testing"
	"time"
)

const testDuration = 12 * time.Minute
const testDurationMs = int64(testDuration / time.Millisecond)

func TestJobWorkerBuilder_JobType(t *testing.T) {
	builder := JobWorkerBuilder{}
	builder.JobType("foo")
	assert.Equal(t, "foo", builder.request.Type)
}

func TestJobWorkerBuilder_Handler(t *testing.T) {
	builder := JobWorkerBuilder{}
	builder.Handler(func(JobClient, entities.Job) {})
	assert.NotNil(t, builder.handler)
}

func TestJobWorkerBuilder_Name(t *testing.T) {
	builder := JobWorkerBuilder{}
	builder.Name("foo")
	assert.Equal(t, "foo", builder.request.Worker)
}

func TestJobWorkerBuilder_Timeout(t *testing.T) {
	builder := JobWorkerBuilder{}
	builder.Timeout(testDuration)
	assert.Equal(t, testDurationMs, builder.request.Timeout)
}

func TestJobWorkerBuilder_BufferSize(t *testing.T) {
	builder := JobWorkerBuilder{}
	builder.BufferSize(123)
	assert.Equal(t, 123, builder.bufferSize)

	// should ignore invalid buffer size
	builder.BufferSize(0)
	assert.Equal(t, 123, builder.bufferSize)
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
