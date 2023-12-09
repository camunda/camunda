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
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"testing"
	"time"

	"github.com/camunda/zeebe/clients/go/v8/internal/containersuite"
	"github.com/camunda/zeebe/clients/go/v8/pkg/entities"
	"github.com/camunda/zeebe/clients/go/v8/pkg/zbc"
	"github.com/stretchr/testify/suite"
)

type integrationTestSuite struct {
	*containersuite.ContainerSuite
	client zbc.Client
}

func TestIntegration(t *testing.T) {
	suite.Run(t, &integrationTestSuite{
		ContainerSuite: &containersuite.ContainerSuite{
			WaitTime:       time.Second,
			ContainerImage: "camunda/zeebe:current-test",
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

	deployment, err := s.client.NewDeployResourceCommand().AddResourceFile("testdata/service_task.bpmn").Send(ctx)
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
	jobsChan := make(chan *entities.Job)
	go s.client.NewStreamJobsCommand().JobType("task").Consumer(jobsChan).Timeout(time.Minute * 5).
		WorkerName("worker").RequestTimeout(time.Duration(1) * time.Minute).Send(ctx)

	// Await until the stream is created on the broker
	// TODO: find a less verbose way of doing this
	streamExists := false
	for start := time.Now(); !streamExists && time.Since(start) < 5*time.Second; {
		response, err := http.Get(fmt.Sprintf("http://%s/actuator/jobstreams/remote", s.MonitoringAddress))
		if err != nil {
			time.Sleep(time.Second)
			continue
		}

		remoteStreams := make([]remoteJobStream, 1)
		responseData, err := io.ReadAll(response.Body)
		if err != nil {
			time.Sleep(time.Second)
			continue
		}

		err = json.Unmarshal(responseData, &remoteStreams)
		if err != nil {
			time.Sleep(time.Second)
			continue
		}

		streamExists = len(remoteStreams) > 0
	}

	s.True(streamExists, "Expected remote stream to exist on broker after 5 seconds, but none yet exist")

	// create two PIs and expect two jobs
	_, err = s.client.NewCreateInstanceCommand().ProcessDefinitionKey(process.GetProcessDefinitionKey()).Send(ctx)
	s.NoError(err)
	_, err = s.client.NewCreateInstanceCommand().ProcessDefinitionKey(process.GetProcessDefinitionKey()).Send(ctx)
	s.NoError(err)

	// then - expect two jobs
	jobs := make([]*entities.Job, 0)
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

type remoteJobStream struct {
	JobType   string                    `json:"jobType"`
	Metadata  remoteJobStreamMetadata   `json:"metadata"`
	Consumers []remoteJobStreamConsumer `json:"consumers"`
}

type remoteJobStreamMetadata struct {
	Worker         string   `json:"worker"`
	Timeout        string   `json:"timeout"`
	FetchVariables []string `json:"fetchVariables"`
}

type remoteJobStreamConsumer struct {
	ID       string `json:"id"`
	Receiver string `json:"receiver"`
}
