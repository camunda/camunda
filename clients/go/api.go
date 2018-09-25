package zbc

import (
	"github.com/zeebe-io/zeebe/clients/go/commands"
)

type ZBClient interface {
	NewHealthCheckCommand() *commands.HealthCheckCommand
	NewDeployWorkflowCommand() *commands.DeployCommand

	NewPublishMessageCommand() commands.PublishMessageCommandStep1
	Close() error
}
