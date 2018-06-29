## Environment

.PHONY: env-up
env-up: env-down
	docker-compose up --force-recreate --build -d

.PHONY: env-down
env-down:
	docker-compose down -v

.PHONY: env-status
env-status:
	docker-compose ps

