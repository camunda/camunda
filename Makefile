# HELP
# This will output the help for each task
# thanks to https://marmelab.com/blog/2016/02/29/auto-documented-makefile.html
.PHONY: help

help: ## This help
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

.DEFAULT_GOAL := help

#
# vscode sync targets
#
.PHONY: vscode-mcp-sync
vscode-mcp-sync: ## Merge repository MCP configuration with user's VS Code MCP settings
	@echo "Syncing VS Code MCP configuration for this repository..."
	@chmod +x ./scripts/vscode-config-sync/sync-mcp.sh
	@./scripts/vscode-config-sync/sync-mcp.sh

.PHONY: vscode-settings-sync
vscode-settings-sync: ## Merge repository settings with user's VS Code settings
	@echo "Syncing VS Code settings for this repository..."
	@chmod +x ./scripts/vscode-config-sync/sync-settings.sh
	@./scripts/vscode-config-sync/sync-settings.sh

.PHONY: vscode-sync-all
vscode-sync-all: vscode-mcp-sync vscode-settings-sync ## Sync both MCP and settings configurations

.PHONY: kafka-exporter-smoke
kafka-exporter-smoke: ## Start local Kafka and verify consume flow for camunda-kafka-exporter
	@echo "Running Kafka exporter smoke test..."
	@bash ./zeebe/exporters/camunda-kafka-exporter/examples/smoke-test.sh

.PHONY: kafka-exporter-smoke-down
kafka-exporter-smoke-down: ## Stop and remove local Kafka used by camunda-kafka-exporter smoke tests
	@docker compose -f ./zeebe/exporters/camunda-kafka-exporter/docker-compose.sm-kafka.yml down -v

.PHONY: kafka-exporter-camunda-stack-up
kafka-exporter-camunda-stack-up: ## Start local Camunda + Kafka integration stack for kafka exporter
	@docker compose -f ./zeebe/exporters/camunda-kafka-exporter/docker-compose.sm-camunda-kafka.yml up -d

.PHONY: kafka-exporter-camunda-stack-down
kafka-exporter-camunda-stack-down: ## Stop local Camunda + Kafka integration stack for kafka exporter
	@docker compose -f ./zeebe/exporters/camunda-kafka-exporter/docker-compose.sm-camunda-kafka.yml down -v
