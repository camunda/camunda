COMMAND_STUB := docker-compose --project-directory ./ -f docker-compose.yml -f ../docker/compose/docker-compose.yaml
ARGS :=

.DEFAULT_GOAL := up
.PHONY: up
up:
	$(COMMAND_STUB) up -d $(ARGS)

.PHONY: down
down:
	$(COMMAND_STUB) down $(ARGS)

.PHONY: stop
stop:
	$(COMMAND_STUB) stop $(ARGS)
