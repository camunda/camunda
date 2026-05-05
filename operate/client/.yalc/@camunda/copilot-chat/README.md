# @camunda/copilot-chat

React component library for embedding Camunda Copilot chat interface into web applications.

## Installation

```bash
npm install @camunda/copilot-chat
```

**Peer Dependencies:**

```bash
npm install react react-dom @carbon/react @carbon/icons-react
```

## Quick Start

```tsx
import { CopilotChat, useChatStore } from '@camunda/copilot-chat';
import '@camunda/copilot-chat/style.css';

function App() {
  const handleSendMessage = async (message: string) => {
    const { addAssistantMessage, appendToMessage, updateMessageStatus } = useChatStore.getState();
    const messageId = `msg-${Date.now()}`;
    addAssistantMessage(messageId);
    appendToMessage(messageId, 'Hello!');
    updateMessageStatus(messageId, 'complete'); // 'pending' | 'streaming' | 'complete' | 'error'
  };

  return <CopilotChat onSendMessage={handleSendMessage} workareaSelector="body" />;
}
```

## With Agent Adapter (Recommended)

For full agent orchestration with tool execution:

```tsx
import { useMemo } from 'react';
import { CopilotChat, useAgentAdapter } from '@camunda/copilot-chat';
import '@camunda/copilot-chat/style.css';

function App() {
  const transport = useMemo(
    () => ({
      subscribe: (id, onEvent) => {
        /* Subscribe to events */
      },
      unsubscribe: (id) => {
        /* Cleanup */
      },
      sendMessage: async (payload) => {
        /* POST to backend */
      },
      sendToolResult: async (payload) => {
        /* POST tool result */
      },
    }),
    []
  );

  const { sendMessage, stopGeneration, resetConversation } = useAgentAdapter({
    transport,
    externalTools: [{ name: 'my_tool', description: '...', parametersSchema: '{}' }],
    onToolInvoke: async (toolName, args) => {
      /* Return result */
    },
  });

  return <CopilotChat onSendMessage={sendMessage} onStopGeneration={stopGeneration} onResetConversation={resetConversation} />;
}
```

## Props

| Prop                    | Type                                 | Required | Description                              |
| ----------------------- | ------------------------------------ | -------- | ---------------------------------------- |
| `onSendMessage`         | `(message: string) => Promise<void>` | Yes      | Called when user sends a message         |
| `onStopGeneration`      | `() => void`                         | No       | Called when user clicks stop button      |
| `onResetConversation`   | `() => void`                         | No       | Called when user resets the conversation |
| `workareaSelector`      | `string`                             | No       | CSS selector for the main content area   |
| `emptyStateTitle`       | `string`                             | No       | Title shown when chat is empty           |
| `emptyStateDescription` | `string`                             | No       | Description shown when chat is empty     |

## Documentation

See the [docs folder](../../docs) for detailed documentation:

- [Architecture Overview](../../docs/architecture.md)
- [Integration Guide](../../docs/integration.md)
- [Writing Adapters](../../docs/adapters.md)

## License

Camunda License 1.0 - See [LICENSE](LICENSE) file for details.
