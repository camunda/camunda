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

package zbc

import (
	"time"

	"github.com/zeebe-io/zeebe/clients/go/commands"
	"github.com/zeebe-io/zeebe/clients/go/worker"
)

type ZBClient interface {
	NewTopologyCommand() *commands.TopologyCommand
	NewDeployWorkflowCommand() *commands.DeployCommand

	NewCreateInstanceCommand() commands.CreateInstanceCommandStep1
	NewCancelInstanceCommand() commands.CancelInstanceStep1
	NewSetVariablesCommand() commands.SetVariablesCommandStep1
	NewResolveIncidentCommand() commands.ResolveIncidentCommandStep1

	NewPublishMessageCommand() commands.PublishMessageCommandStep1

	NewActivateJobsCommand() commands.ActivateJobsCommandStep1
	NewCompleteJobCommand() commands.CompleteJobCommandStep1
	NewFailJobCommand() commands.FailJobCommandStep1
	NewUpdateJobRetriesCommand() commands.UpdateJobRetriesCommandStep1

	NewJobWorker() worker.JobWorkerBuilderStep1

	SetRequestTimeout(time.Duration) ZBClient

	Close() error
}
