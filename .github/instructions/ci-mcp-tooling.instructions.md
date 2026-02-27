---

applyTo: ".vscode/mcp.json,.github/instructions/*.instructions.md,.github/copilot-instructions.md"
--------------------------------------------------------------------------------------------------

# CI/Release MCP Tooling — Agent Instructions

This file covers MCP work in the **CI/release/DX context only**.
It is NOT about implementing the `gateways/gateway-mcp` Java module.

## What "MCP work" means here

When asked about MCP in a CI, release, or developer experience context, the relevant tasks are:

- **Shared MCP configuration**: maintaining `.vscode/mcp.json` with shared MCP server entries
- **Documentation**: writing or updating setup guides for MCP servers (GitHub MCP, Playwright MCP, Context7 MCP, Camunda MCP)
- **Docker MCP Toolkit evaluation**: comparing Docker MCP Toolkit vs manual MCP setup (usability, steps, complexity)
- **Copilot Agent tool restrictions**: testing whether tool allow/deny lists work and documenting findings
- **Agent instruction files**: creating or updating `.github/instructions/*.instructions.md` to guide Copilot agents

## What "MCP work" does NOT mean here

- Do **not** open or modify files under `gateways/gateway-mcp/`
- Do **not** apply `gateway-mcp-tools.instructions.md` — that file is for the Java Spring AI MCP server implementation
- Do **not** write Java code for MCP tools

