package docker

import (
	"context"
	"os"
	"scripts/camunda-core/pkg/executil"
	"scripts/camunda-core/pkg/logging"
	"scripts/camunda-core/pkg/utils"
)

func EnsureDockerLogin(ctx context.Context, username, password string) error {
	if username == "" {
		username = utils.FirstNonEmpty(os.Getenv("TEST_DOCKER_USERNAME_CAMUNDA_CLOUD"), os.Getenv("NEXUS_USERNAME"))
	}
	if password == "" {
		password = utils.FirstNonEmpty(os.Getenv("TEST_DOCKER_PASSWORD_CAMUNDA_CLOUD"), os.Getenv("NEXUS_PASSWORD"))
	}
	if username == "" || password == "" {
		logging.Logger.Debug().Msg("skipping docker login (credentials not provided)")
		return nil
	}

	logging.Logger.Debug().Str("registry", "docker.io").Msg("ensuring docker login")

	args := []string{"login", "--username", username, "--password-stdin"}
	return executil.RunCommandWithStdin(ctx, "docker", args, nil, "", []byte(password))
}
