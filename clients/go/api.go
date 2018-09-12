package zbc

import (
	"github.com/zeebe-io/zeebe/clients/go/commands"
)

type ZBClient interface {
	NewHealthCheckCommand() *commands.HealthCheckCommand
	NewDeployWorkflowCommand() *commands.DeployCommand

	Close() error
}