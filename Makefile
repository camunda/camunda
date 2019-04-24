## Environment

.PHONY: env-up
env-up: env-down
	docker-compose up --force-recreate --build -d elasticsearch kibana zeebe operate

.PHONY: env-down
env-down:
	docker-compose down -v

.PHONY: env-status
env-status:
	docker-compose ps

.PHONY: env-clean
env-clean: env-down
	docker system prune -a
