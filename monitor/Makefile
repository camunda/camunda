COMMAND_STUB := docker-compose --project-directory ./ -f docker-compose.yml -f ../docker/compose/docker-compose.yaml

.DEFAULT_GOAL := up
.PHONY: up
up:
	$(COMMAND_STUB) up -d

.PHONY: down
down:
	$(COMMAND_STUB) down

