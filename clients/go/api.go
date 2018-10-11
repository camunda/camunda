package zbc

import (
	"github.com/zeebe-io/zeebe/clients/go/commands"
)

type ZBClient interface {
	NewTopologyCommand() *commands.TopologyCommand
	NewDeployWorkflowCommand() *commands.DeployCommand

	NewCancelInstanceCommand() commands.CancelInstanceStep1
	NewCreateInstanceCommand() commands.CreateInstanceCommandStep1

	NewPublishMessageCommand() commands.PublishMessageCommandStep1
	NewCreateJobCommand() commands.CreateJobCommandStep1
	Close() error
}
