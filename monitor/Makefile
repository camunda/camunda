COMPOSE     := docker compose --project-directory ./
COMPOSE_DEV := $(COMPOSE) -f docker-compose.yml -f docker-compose.dev.yml
COMPOSE     := $(COMPOSE) -f docker-compose.yml

.DEFAULT_GOAL := up
.PHONY: up down clean up-dev down-dev clean-dev

up:
	$(COMPOSE) up -d

down:
	$(COMPOSE) down

clean:
	$(COMPOSE) down -v

up-dev:
	$(COMPOSE_DEV) up -d

down-dev:
	$(COMPOSE_DEV) down

clean-dev:
	$(COMPOSE_DEV) down -v

# Deprecated: Grizzly is archived and has no Windows support. Use make up-dev instead.
.PHONY: grizzly
grizzly:
	@set -o allexport && source .env && \
	grr serve ./grafana/dashboards --port 8093 --watch
