COMMAND_STUB_ZEEBE := docker-compose --project-directory ./ -f docker-compose.yml -f ../docker/compose/docker-compose.yaml
COMMAND_STUB_OPE := docker-compose --project-directory ./ -f docker-compose.yml -f docker-compose.ope-ztl.yml

.DEFAULT_GOAL := up
.PHONY: up
up:
	$(COMMAND_STUB_ZEEBE) up -d

.PHONY: down
down:
	$(COMMAND_STUB_ZEEBE) down

.PHONY: up-ope
up-ope:
	$(COMMAND_STUB_OPE) up -d --force-recreate

.PHONY: down-ope
down-ope:
	$(COMMAND_STUB_OPE) down


.PHONY: clean
clean:
	$(COMMAND_STUB_ZEEBE) down -v

.PHONY: clean-ope
clean-ope:
	$(COMMAND_STUB_OPE) down -v
