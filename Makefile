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

#
# claude sync targets
#
.PHONY: claude-mcp-sync
claude-mcp-sync: ## Merge repository MCP configuration with user's Claude .mcp.json
	@echo "Syncing Claude MCP configuration for this repository..."
	@chmod +x ./scripts/vscode-config-sync/sync-claude-mcp.sh
	@./scripts/vscode-config-sync/sync-claude-mcp.sh

.PHONY: claude-settings-sync
claude-settings-sync: ## Merge repository policy into user's .claude/settings.json
	@echo "Syncing Claude settings for this repository..."
	@chmod +x ./scripts/vscode-config-sync/sync-claude-settings.sh
	@./scripts/vscode-config-sync/sync-claude-settings.sh

.PHONY: claude-sync-all
claude-sync-all: claude-mcp-sync claude-settings-sync ## Sync all Claude configurations

.PHONY: ai-sync-all
ai-sync-all: vscode-sync-all claude-sync-all ## Sync all AI assistant configurations (VS Code + Claude)
