# Chatbot Component

A self-contained chatbot component for the Operate React frontend that connects **directly** to LLM providers (OpenAI, Anthropic) and integrates with the Camunda MCP (Model Context Protocol) Gateway.

**No backend proxy required!**

## Features

- 💬 Real-time chat interface with Carbon Design System styling
- 🤖 Direct LLM connection (OpenAI GPT-4, Anthropic Claude)
- 🔧 MCP Gateway integration for Camunda-specific tool calls
- 🎨 Theme-aware (supports light/dark mode)
- ♿ Accessible (ARIA labels, keyboard navigation)
- 📱 Responsive design

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Operate Frontend                      │
│  ┌─────────────────────────────────────────────────┐    │
│  │              Chatbot Component                   │    │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────────┐  │    │
│  │  │   UI     │  │ useChat  │  │  chatbotStore │  │    │
│  │  │(Carbon)  │◄─┤  Hook    │◄─┤   (MobX)      │  │    │
│  │  └──────────┘  └────┬─────┘  └──────────────┘  │    │
│  │                     │                           │    │
│  │              ┌──────┴──────┐                    │    │
│  │              │             │                    │    │
│  │         ┌────▼────┐  ┌─────▼─────┐             │    │
│  │         │llmClient│  │ mcpClient │             │    │
│  │         └────┬────┘  └─────┬─────┘             │    │
│  └──────────────┼─────────────┼────────────────────┘    │
│                 │             │                          │
└─────────────────┼─────────────┼──────────────────────────┘
                  │             │
                  ▼             ▼
       ┌──────────────────┐  ┌─────────────────────┐
       │   LLM Provider   │  │   MCP Gateway       │
       │ (OpenAI/Anthropic│  │ (Camunda Tools)     │
       │   Direct API)    │  │                     │
       └──────────────────┘  └─────────────────────┘
```

## Usage

### Basic Usage

The chatbot is automatically rendered in the Layout when enabled and configured:

```tsx
import {chatbotStore} from 'modules/stores/chatbot';

// Enable the chatbot
chatbotStore.enable();

// Configure LLM (API key required)
chatbotStore.setLLMConfig({
  provider: 'openai',  // or 'anthropic'
  apiKey: 'sk-...',    // Your API key
  model: 'gpt-4o',     // Optional, defaults to gpt-4o
});

// Configure MCP Gateway
chatbotStore.setMcpConfig({
  baseUrl: 'http://localhost:8080/mcp',
});
```

### Standalone Usage

```tsx
import {Chatbot} from 'modules/components/Chatbot';

<Chatbot
  llmConfig={{
    provider: 'openai',
    apiKey: 'sk-...',
    model: 'gpt-4o',
  }}
  mcpConfig={{
    baseUrl: 'http://localhost:8080/mcp',
  }}
  placeholder="Ask about your processes..."
/>
```

### Using the Chat Hook Directly

```tsx
import {useChat} from 'modules/components/Chatbot/useChat';

function MyComponent() {
  const {
    messages,
    input,
    setInput,
    isLoading,
    sendMessage,
    availableTools,
  } = useChat({
    llmConfig: {
      provider: 'anthropic',
      apiKey: 'sk-ant-...',
      model: 'claude-sonnet-4-20250514',
    },
    mcpConfig: {
      baseUrl: '/mcp',
    },
  });

  // Custom UI implementation
}
```

## Configuration

### LLM Configuration

```typescript
type LLMConfig = {
  provider: 'openai' | 'anthropic' | 'perplexity' | 'custom';
  apiKey: string;           // Required: Your API key
  model?: string;           // Optional: Model name (defaults based on provider)
  baseUrl?: string;         // Optional: Custom API endpoint
  maxTokens?: number;       // Optional: Max response tokens (default: 4096)
  temperature?: number;     // Optional: Response creativity (default: 0.7)
};
```

### MCP Configuration

```typescript
type McpClientConfig = {
  baseUrl: string;          // MCP gateway URL
  authToken?: string;       // Optional: Auth token for the gateway
};
```

## Supported LLM Providers

| Provider | Models | Tool Calling | Notes |
|----------|--------|--------------|-------|
| OpenAI | gpt-4o, gpt-4-turbo, gpt-3.5-turbo | ✅ Yes | **Recommended** - Best tool calling support |
| Anthropic | claude-sonnet-4-20250514, claude-3-opus | ✅ Yes | Excellent tool calling support |
| Azure OpenAI | gpt-4o, gpt-4 | ✅ Yes | Enterprise option, same models as OpenAI |
| GitHub Models | gpt-4o, gpt-4, claude, etc. | ✅ Yes | Use GitHub PAT for authentication |
| Perplexity | sonar-pro, sonar | ❌ No | Web search only, no MCP tool support |
| Custom | Any OpenAI-compatible API | Varies | Set `baseUrl` to your endpoint |

> ⚠️ **Important**: Perplexity AI does NOT support function/tool calling. If you need MCP tool integration (to query process instances, incidents, etc.), use **OpenAI**, **Anthropic**, **Azure OpenAI**, or **GitHub Models**.

### Using OpenAI (Recommended for MCP Tools)

```typescript
import {chatbotStore} from 'modules/stores/chatbot';

chatbotStore.enable();
chatbotStore.setLLMConfig({
  provider: 'openai',
  apiKey: 'sk-...', // Your OpenAI API key
  model: 'gpt-4o',  // Best for function calling
});
```

### Using Azure OpenAI

```typescript
chatbotStore.setLLMConfig({
  provider: 'custom',
  apiKey: 'your-azure-openai-key',
  baseUrl: 'https://YOUR-RESOURCE.openai.azure.com/openai/deployments/YOUR-DEPLOYMENT',
  model: 'gpt-4o',
});
```

### Using GitHub Models (Preview)

GitHub Models provides access to various LLMs using your GitHub Personal Access Token:

```typescript
chatbotStore.setLLMConfig({
  provider: 'custom',
  apiKey: 'github_pat_...', // GitHub PAT with models:read scope
  baseUrl: 'https://models.inference.ai.azure.com',
  model: 'gpt-4o', // or 'claude-3.5-sonnet', 'meta-llama-3.1-70b', etc.
});
```

To get a GitHub PAT for Models:
1. Go to https://github.com/settings/tokens
2. Create a token with `models:read` scope
3. Use it as the `apiKey`

### Using Anthropic

```typescript
chatbotStore.setLLMConfig({
  provider: 'anthropic',
  apiKey: 'sk-ant-...', // Your Anthropic API key
  model: 'claude-sonnet-4-20250514',
});
```

### Using Perplexity AI Pro (No Tool Support)

Perplexity is great for web search queries but **cannot execute MCP tools**:

```typescript
chatbotStore.setLLMConfig({
  provider: 'perplexity',
  apiKey: 'pplx-...', // Your Perplexity API key
  model: 'sonar-pro',
});
```

## How It Works

1. **Chatbot initializes** → Fetches available tools from MCP Gateway via `tools/list` JSON-RPC call
2. **User sends a message** → Message added to conversation history
3. **Call LLM with tools** → Browser makes direct API call to LLM provider, including tool definitions
4. **LLM may request tools** → If LLM wants to use a tool, it returns `tool_calls` in the response
5. **Execute tools via MCP** → Tool calls sent to MCP Gateway via `tools/call` JSON-RPC
6. **Return results to LLM** → Tool results sent back to LLM for final response generation
7. **Display response** → Assistant message shown with any tool call results

### MCP Protocol

The chatbot uses the [Model Context Protocol](https://modelcontextprotocol.io/) to communicate with the Camunda MCP Gateway:

- **Transport**: Streamable HTTP at `/mcp` endpoint
- **Protocol**: JSON-RPC 2.0
- **Methods used**:
  - `tools/list` - Discover available tools
  - `tools/call` - Execute a tool with arguments

### Debugging

Open browser DevTools console to see:
- `[MCP] Loaded X tools: [...]` - Tools discovered from gateway
- `[LLM] Sending request with X tools to Perplexity` - Tools sent to LLM
- `[LLM] Tool call requested: toolName` - LLM wants to call a tool
- `[MCP] Calling tool: toolName` - Tool being executed
- `[MCP] Tool result: {...}` - Result from tool execution
- `[Navigation] Navigating to: /path` - Navigation command executed

## Navigation

The chatbot can navigate to different pages within Operate. When the user asks to "show", "open", or "go to" something, the LLM will include a navigation command in its response.

### Supported Navigation Commands

| Command | Description | Example User Request |
|---------|-------------|---------------------|
| `[NAVIGATE:processInstance:KEY]` | Opens process instance detail page | "Show me instance 123" |
| `[NAVIGATE:processDefinition:KEY]` | Opens process definition view | "Open the order process" |
| `[NAVIGATE:incidents]` | Opens the incidents page | "Show me all incidents" |
| `[NAVIGATE:processes]` | Opens the processes list | "Go to processes" |
| `[NAVIGATE:dashboard]` | Opens the dashboard | "Take me to the dashboard" |

### How It Works

1. User asks to see something (e.g., "show me instance 2251799813685319")
2. LLM queries the MCP tools to verify the instance exists
3. LLM includes a navigation command in its response: `[NAVIGATE:processInstance:2251799813685319]`
4. The chatbot parses the command and navigates to `/processes/2251799813685319`
5. The navigation command is stripped from the displayed message

## Available MCP Tools

The Camunda MCP Gateway provides these tools (from `gateway-mcp`):

- **Cluster Tools**: Get cluster status, topology
- **Process Tools**: List/query process definitions and instances
- **Incident Tools**: Query and manage incidents
- **Variable Tools**: Get/set process variables

Tools are automatically discovered from the MCP gateway at startup.

## Security Considerations

⚠️ **API Key Handling**:
- API keys are stored in memory only (not persisted to localStorage)
- Keys are sent directly to the LLM provider over HTTPS
- Never commit API keys to the repository
- Consider using environment variables for development

For Anthropic, the `anthropic-dangerous-direct-browser-access` header is used. This is safe for internal tools but not recommended for public-facing applications.

## Styling

The component uses Carbon Design System tokens for theming:

- `--cds-layer` - Background colors
- `--cds-text-primary` - Text colors
- `--cds-button-primary` - Accent colors
- `--cds-spacing-*` - Spacing

## Development

### Running Locally

1. Start the Camunda platform with MCP gateway enabled
2. Enable and configure the chatbot:

```typescript
import {chatbotStore} from 'modules/stores/chatbot';

chatbotStore.enable();
chatbotStore.setApiKey('your-api-key');
chatbotStore.setMcpConfig({baseUrl: 'http://localhost:8080/mcp'});
```

### Testing

```bash
npm run test -- --filter=Chatbot
```

## Files

- `index.tsx` - Main Chatbot component with UI
- `useChat.ts` - React hook managing chat state and LLM/MCP communication
- `llmClient.ts` - Direct LLM API client (OpenAI, Anthropic)
- `mcpClient.ts` - MCP Gateway client for tool discovery and execution
- `types.ts` - TypeScript type definitions
- `styled.tsx` - Styled components using Carbon Design System

## Future Enhancements

- [ ] Streaming responses (real-time token display)
- [ ] Message persistence (session storage)
- [ ] API key input modal in UI
- [ ] File/image attachments
- [ ] Voice input
- [ ] Markdown rendering with syntax highlighting
- [ ] Conversation export
- [ ] Multiple conversation threads
