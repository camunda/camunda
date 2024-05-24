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

package test

import (
	"context"
	"fmt"
	"os"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/camunda/zeebe/clients/go/v8/internal/containersuite"
	"github.com/camunda/zeebe/clients/go/v8/internal/utils"
	"github.com/camunda/zeebe/clients/go/v8/pkg/entities"
	"github.com/camunda/zeebe/clients/go/v8/pkg/worker"
	"github.com/camunda/zeebe/clients/go/v8/pkg/zbc"
	"github.com/google/uuid"
	"github.com/stretchr/testify/suite"
)

const dockerImageName = "camunda/zeebe:current-test"

type integrationTestSuite struct {
	*containersuite.ContainerSuite
	client zbc.Client
}

func TestIntegration(t *testing.T) {
	suite.Run(t, &integrationTestSuite{
		ContainerSuite: &containersuite.ContainerSuite{
			WaitTime:       time.Second,
			ContainerImage: dockerImageName,
		},
	})
}

func (s *integrationTestSuite) SetupSuite() {
	var err error
	s.ContainerSuite.SetupSuite()

	s.client, err = zbc.NewClient(&zbc.ClientConfig{
		GatewayAddress:         s.GatewayAddress,
		UsePlaintextConnection: true,
	})
	if err != nil {
		s.T().Fatal(err)
	}
}

func (s *integrationTestSuite) TearDownSuite() {
	err := s.client.Close()
	if err != nil {
		s.T().Fatal(err)
	}

	s.ContainerSuite.TearDownSuite()
}

func (s *integrationTestSuite) TestTopology() {
	// when
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	response, err := s.client.NewTopologyCommand().Send(ctx)
	if err != nil {
		s.T().Fatal(err)
	}

	// then
	s.EqualValues(1, response.GetClusterSize())
	s.EqualValues(1, response.GetPartitionsCount())
	s.EqualValues(1, response.GetReplicationFactor())
	s.NotEmpty(response.GetGatewayVersion())

	for _, broker := range response.GetBrokers() {
		s.EqualValues(response.GetGatewayVersion(), broker.GetVersion())
	}
}

func (s *integrationTestSuite) TestDeployProcess() {
	// when
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	deployment, err := s.client.NewDeployResourceCommand().AddResourceFile("testdata/service_task.bpmn").Send(ctx)
	if err != nil {
		s.T().Fatal(err)
	}

	// then
	s.Greater(deployment.GetKey(), int64(0))

	deployedResource := deployment.GetDeployments()[0]
	s.NotNil(deployedResource)

	process := deployedResource.GetProcess()
	s.NotNil(process)
	s.EqualValues("deploy_process", process.GetBpmnProcessId())
	s.EqualValues(int32(1), process.GetVersion())
	s.Greater(process.GetProcessDefinitionKey(), int64(0))
}

func (s *integrationTestSuite) TestDeployForm() {
	// when
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	deployment, err := s.client.NewDeployResourceCommand().AddResourceFile("testdata/deploy_form.form").Send(ctx)
	if err != nil {
		s.T().Fatal(err)
	}

	// then
	s.Greater(deployment.GetKey(), int64(0))

	deployedResource := deployment.GetDeployments()[0]
	s.NotNil(deployedResource)

	form := deployedResource.GetForm()
	s.NotNil(form)
	s.EqualValues("simple_form", form.GetFormId())
	s.EqualValues("testdata/deploy_form.form", form.GetResourceName())
}

func (s *integrationTestSuite) TestCreateInstance() {
	// given
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	deployment, err := s.client.NewDeployResourceCommand().AddResourceFile("testdata/service_task.bpmn").Send(ctx)
	if err != nil {
		s.T().Fatal(err)
	}

	// when
	ctx, cancel = context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	deployedResource := deployment.GetDeployments()[0]
	s.NotNil(deployedResource)

	process := deployedResource.GetProcess()
	s.NotNil(process)
	processInstance, err := s.client.NewCreateInstanceCommand().BPMNProcessId("deploy_process").Version(process.GetVersion()).Send(ctx)
	if err != nil {
		s.T().Fatal(err)
	}

	// then
	s.EqualValues(process.GetVersion(), processInstance.GetVersion())
	s.EqualValues(process.GetProcessDefinitionKey(), processInstance.GetProcessDefinitionKey())
	s.EqualValues(process.GetBpmnProcessId(), processInstance.GetBpmnProcessId())
	s.Greater(processInstance.GetProcessInstanceKey(), int64(0))
}

func (s *integrationTestSuite) TestEvaluateDecision() {
	// given
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	variables := "{\"lightsaberColor\":\"blue\"}"

	deployment, err := s.client.NewDeployResourceCommand().AddResourceFile("testdata/drg-force-user.dmn").Send(ctx)
	if err != nil {
		s.T().Fatal(err)
	}
	deployedResource := deployment.GetDeployments()[0]
	s.NotNil(deployedResource)
	decision := deployedResource.GetDecision()
	s.NotNil(decision)

	// when
	evalDecisionCommand, err := s.client.NewEvaluateDecisionCommand().DecisionId("jedi_or_sith").VariablesFromString(variables)
	if err != nil {
		s.T().Fatal(err)
	}
	evaluationResponse, err := evalDecisionCommand.Send(context.Background())
	if err != nil {
		s.T().Fatal(err)
	}

	// then
	s.EqualValues(evaluationResponse.GetDecisionId(), decision.GetDmnDecisionId())
	s.EqualValues(evaluationResponse.GetDecisionKey(), decision.GetDecisionKey())
	s.EqualValues(evaluationResponse.GetDecisionName(), decision.GetDmnDecisionName())
	s.EqualValues(evaluationResponse.GetDecisionVersion(), decision.GetVersion())
	s.EqualValues(evaluationResponse.GetDecisionRequirementsKey(), decision.GetDecisionRequirementsKey())
	s.EqualValues(evaluationResponse.GetDecisionRequirementsId(), decision.GetDmnDecisionRequirementsId())
	s.EqualValues(evaluationResponse.GetDecisionOutput(), "\"Jedi\"")
	s.EqualValues(evaluationResponse.GetFailedDecisionId(), "")
	s.EqualValues(evaluationResponse.GetFailureMessage(), "")
}

func (s *integrationTestSuite) TestActivateJobs() {
	// given
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	deployment, err := s.client.NewDeployResourceCommand().AddResourceFile("testdata/service_task.bpmn").Send(ctx)
	if err != nil {
		s.T().Fatal(err)
	}

	ctx, cancel = context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	deployedResource := deployment.GetDeployments()[0]
	s.NotNil(deployedResource)

	process := deployedResource.GetProcess()
	s.NotNil(process)
	_, err = s.client.NewCreateInstanceCommand().ProcessDefinitionKey(process.GetProcessDefinitionKey()).Send(ctx)
	if err != nil {
		s.T().Fatal(err)
	}

	// when
	ctx, cancel = context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	jobs, err := s.client.NewActivateJobsCommand().JobType("task").MaxJobsToActivate(1).Timeout(time.Minute * 5).WorkerName("worker").Send(ctx)
	if err != nil {
		s.T().Fatal(err)
	}

	// then
	for _, job := range jobs {
		s.EqualValues(process.GetProcessDefinitionKey(), job.GetProcessDefinitionKey())
		s.EqualValues(process.GetBpmnProcessId(), job.GetBpmnProcessId())
		s.EqualValues("service_task", job.GetElementId())
		s.Greater(job.GetRetries(), int32(0))

		ctx, cancel = context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()

		jobResponse, err := s.client.NewCompleteJobCommand().JobKey(job.Key).Send(ctx)
		if err != nil {
			s.T().Fatal(err)
		}

		if jobResponse == nil {
			s.T().Fatal("Empty complete job response")
		}
	}
}

func (s *integrationTestSuite) TestStreamJobs() {
	// given
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	taskType := uuid.NewString()
	bpmn, err := readBpmnWithCustomJobType("testdata/service_task.bpmn", taskType)
	s.NoError(err)

	deployment, err := s.client.NewDeployResourceCommand().AddResource(bpmn, "service_task.bpmn").Send(ctx)
	if err != nil {
		s.T().Fatal(err)
	}

	deployedResource := deployment.GetDeployments()[0]
	s.NotNil(deployedResource)

	process := deployedResource.GetProcess()
	s.NotNil(process)

	// when
	// the stream will be closed by the deferred context cancellation
	ctx, cancel = context.WithCancel(context.Background())
	defer cancel()

	jobsChan := make(chan entities.Job)
	defer close(jobsChan)

	workerName := uuid.New().String()
	go s.client.NewStreamJobsCommand().JobType(taskType).Consumer(jobsChan).Timeout(time.Minute * 5).
		WorkerName(workerName).RequestTimeout(time.Duration(1) * time.Minute).Send(ctx)

	// Await until the stream is created on the broker
	streamExists := utils.AwaitJobStreamExists(workerName, s.MonitoringAddress)
	s.True(streamExists, "Expected remote stream to exist on broker after 5 seconds, but none yet exist")

	// create two PIs and expect two jobs
	_, err = s.client.NewCreateInstanceCommand().ProcessDefinitionKey(process.GetProcessDefinitionKey()).Send(ctx)
	s.NoError(err)
	_, err = s.client.NewCreateInstanceCommand().ProcessDefinitionKey(process.GetProcessDefinitionKey()).Send(ctx)
	s.NoError(err)

	// then - expect two jobs
	jobs := make([]entities.Job, 0)
	for i := 0; i < 2; i++ {
		job, ok := <-jobsChan
		if ok {
			jobs = append(jobs, job)
		} else {
			break
		}
	}
	s.Len(jobs, 2, "Expected to receive 2 jobs")

	for _, job := range jobs {
		s.EqualValues(process.GetProcessDefinitionKey(), job.GetProcessDefinitionKey())
		s.EqualValues(process.GetBpmnProcessId(), job.GetBpmnProcessId())
		s.EqualValues("service_task", job.GetElementId())
		s.Greater(job.GetRetries(), int32(0))
	}
}

func (s *integrationTestSuite) TestFailJob() {
	// given
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	deployment, err := s.client.NewDeployResourceCommand().AddResourceFile("testdata/service_task.bpmn").Send(ctx)
	if err != nil {
		s.T().Fatal(err)
	}

	ctx, cancel = context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	deployedResource := deployment.GetDeployments()[0]
	s.NotNil(deployedResource)

	process := deployedResource.GetProcess()
	s.NotNil(process)
	_, err = s.client.NewCreateInstanceCommand().ProcessDefinitionKey(process.GetProcessDefinitionKey()).Send(ctx)
	if err != nil {
		s.T().Fatal(err)
	}

	// when
	ctx, cancel = context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	jobs, err := s.client.NewActivateJobsCommand().JobType("task").MaxJobsToActivate(1).Timeout(time.Minute * 5).WorkerName("worker").Send(ctx)
	if err != nil {
		s.T().Fatal(err)
	}

	// then
	for _, job := range jobs {
		ctx, cancel = context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()

		failedJob, err := s.client.NewFailJobCommand().JobKey(job.GetKey()).Retries(0).Send(ctx)
		if err != nil {
			s.T().Fatal(err)
		}

		if failedJob == nil {
			s.T().Fatal("Empty fail job response")
		}
	}
}

func (s *integrationTestSuite) TestStreamingJobWorker() {
	// given
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	taskType := uuid.NewString()
	bpmn, err := readBpmnWithCustomJobType("testdata/service_task.bpmn", taskType)
	s.NoError(err)

	deployment, err := s.client.NewDeployResourceCommand().AddResource(bpmn, "service_task.bpmn").Send(ctx)
	if err != nil {
		s.T().Fatal(err)
	}

	deployedResource := deployment.GetDeployments()[0]
	s.NotNil(deployedResource)

	process := deployedResource.GetProcess()
	s.NotNil(process)

	// when
	// the stream will be closed by the deferred context cancellation
	ctx, cancel = context.WithCancel(context.Background())
	defer cancel()

	jobsChan := make(chan entities.Job)
	defer close(jobsChan)

	workerName := uuid.New().String()
	jobWorker := s.client.NewJobWorker().
		JobType(taskType).
		Handler(func(client worker.JobClient, job entities.Job) {
			jobsChan <- job
		}).
		Timeout(time.Minute * 5).
		PollInterval(1 * time.Hour). // poll very slowly to make sure we get our jobs from streaming
		Name(workerName).
		StreamEnabled(true).
		RequestTimeout(time.Duration(1) * time.Second).
		Open()
	defer jobWorker.Close()

	// Await until the stream is created on the broker
	streamExists := utils.AwaitJobStreamExists(workerName, s.MonitoringAddress)
	s.True(streamExists, "Expected remote stream to exist on broker after 5 seconds, but none yet exist")

	// create two PIs and expect two jobs
	_, err = s.client.NewCreateInstanceCommand().ProcessDefinitionKey(process.GetProcessDefinitionKey()).Send(ctx)
	s.NoError(err)
	_, err = s.client.NewCreateInstanceCommand().ProcessDefinitionKey(process.GetProcessDefinitionKey()).Send(ctx)
	s.NoError(err)

	// then - expect two jobs
	jobs := make([]entities.Job, 0)
	for i := 0; i < 2; i++ {
		job, ok := <-jobsChan
		if ok {
			jobs = append(jobs, job)
		} else {
			break
		}
	}
	s.Len(jobs, 2, "Expected to receive 2 jobs")

	jobKeys := make([]int64, 0)
	for _, job := range jobs {
		s.NotContains(jobKeys, job.Key)
		s.EqualValues(process.GetProcessDefinitionKey(), job.GetProcessDefinitionKey())
		s.EqualValues(process.GetBpmnProcessId(), job.GetBpmnProcessId())
		s.EqualValues("service_task", job.GetElementId())
		s.Greater(job.GetRetries(), int32(0))

		jobKeys = append(jobKeys, job.Key)
	}
}

type slowWorkerSuite struct {
	*integrationTestSuite
}

func TestSlowWorker(t *testing.T) {
	suite.Run(t, &slowWorkerSuite{
		integrationTestSuite: &integrationTestSuite{
			ContainerSuite: &containersuite.ContainerSuite{
				WaitTime:       time.Second,
				ContainerImage: dockerImageName,
				Env: map[string]string{
					"ZEEBE_DEBUG":     "true",
					"ZEEBE_LOG_LEVEL": "debug",
				},
			},
		},
	})
}

func (s *slowWorkerSuite) TestSlowStreamingJobWorker() {
	// given
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	taskType := uuid.NewString()
	bpmn, err := readBpmnWithCustomJobType("testdata/service_task.bpmn", taskType)
	s.NoError(err)

	deployment, err := s.client.NewDeployResourceCommand().AddResource(bpmn, "service_task.bpmn").Send(ctx)
	if err != nil {
		s.T().Fatal(err)
	}

	deployedResource := deployment.GetDeployments()[0]
	s.NotNil(deployedResource)

	process := deployedResource.GetProcess()
	s.NotNil(process)

	didYield := atomic.Bool{}
	s.ContainerSuite.ConsumeLogs(func(log string) {
		if strings.Contains(log, "\"valueType\":\"JOB\"") && strings.Contains(log, "\"intent\":\"YIELDED\"") {
			didYield.Store(true)
		}
	})

	// when
	// the stream will be closed by the deferred context cancellation
	ctx, cancel = context.WithCancel(context.Background())
	defer cancel()

	jobsChan := make(chan entities.Job)
	defer close(jobsChan)

	workerName := uuid.New().String()
	jobWorker := s.client.NewJobWorker().
		JobType(taskType).
		Handler(func(client worker.JobClient, job entities.Job) {
			jobsChan <- job
		}).
		Timeout(time.Minute * 5).
		PollInterval(1 * time.Hour). // poll very slowly to make sure we get our jobs from streaming
		Name(workerName).
		Concurrency(1).
		MaxJobsActive(1).
		StreamEnabled(true).
		RequestTimeout(time.Duration(1) * time.Second).
		Open()
	defer jobWorker.Close()

	// Await until the stream is created on the broker
	streamExists := utils.AwaitJobStreamExists(workerName, s.MonitoringAddress)
	s.True(streamExists, "Expected remote stream to exist on broker after 5 seconds, but none yet exist")

	// create PIs until we start yielding; since we only yield once buffers fill up, it's hard to predict how fast
	// it will take to do so. we can cheat by using 32KB has our payload size, which at the time of writing, is
	// the hard-coded  threshold for a gRPC stream to become not ready
	variables := map[string]interface{}{"foo": strings.Repeat("x", 32*1024)}
	for start := time.Now(); !didYield.Load() && time.Since(start) < 30*time.Second; {
		cmd, err := s.client.NewCreateInstanceCommand().ProcessDefinitionKey(process.GetProcessDefinitionKey()).VariablesFromMap(variables)
		s.Require().NoError(err)
		_, err = cmd.Send(ctx)
		s.Require().NoError(err)
		time.Sleep(100 * time.Millisecond)
	}
	s.Require().Truef(didYield.Load(), "Expected to have yielded at least one job within 30 seconds, but did not; did back pressure not kick in?")

	// then - consume any remaining jobs
	jobs := make([]entities.Job, 0)
jobConsumingLoop:
	for {
		select {
		case job := <-jobsChan:
			jobs = append(jobs, job)
		case <-time.After(5 * time.Second):
			break jobConsumingLoop
		}
	}

	s.Require().NotEmptyf(jobs, "Expected to receive some jobs")
	jobKeys := make([]int64, 0)
	for _, job := range jobs {
		s.NotContains(jobKeys, job.Key)
		s.EqualValues(process.GetProcessDefinitionKey(), job.GetProcessDefinitionKey())
		s.EqualValues(process.GetBpmnProcessId(), job.GetBpmnProcessId())
		s.EqualValues("service_task", job.GetElementId())
		s.Greater(job.GetRetries(), int32(0))

		jobKeys = append(jobKeys, job.Key)
	}
}

func readBpmnWithCustomJobType(path string, jobType string) ([]byte, error) {
	b, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}

	parsed := string(b)
	bpmn := strings.ReplaceAll(parsed, "<zeebe:taskDefinition type=\"task\" />", fmt.Sprintf("<zeebe:taskDefinition type=\"%s\" />", jobType))

	return []byte(bpmn), nil
}

func (s *integrationTestSuite) TestUpdateJobTimeout() {
	// given
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	deployment, err := s.client.NewDeployResourceCommand().AddResourceFile("testdata/service_task.bpmn").Send(ctx)
	if err != nil {
		s.T().Fatal(err)
	}

	ctx, cancel = context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	deployedResource := deployment.GetDeployments()[0]
	s.NotNil(deployedResource)

	process := deployedResource.GetProcess()
	s.NotNil(process)
	_, err = s.client.NewCreateInstanceCommand().ProcessDefinitionKey(process.GetProcessDefinitionKey()).Send(ctx)
	if err != nil {
		s.T().Fatal(err)
	}

	// when
	ctx, cancel = context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	jobs, err := s.client.NewActivateJobsCommand().JobType("task").MaxJobsToActivate(1).Timeout(time.Minute * 5).WorkerName("worker").Send(ctx)
	if err != nil {
		s.T().Fatal(err)
	}

	// then
	for _, job := range jobs {
		ctx, cancel = context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()

		failedJob, err := s.client.NewUpdateJobTimeoutCommand().JobKey(job.GetKey()).Timeout(50000).Send(ctx)
		if err != nil {
			s.T().Fatal(err)
		}

		if failedJob == nil {
			s.T().Fatal("Empty fail job response")
		}
	}
}
