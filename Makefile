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
