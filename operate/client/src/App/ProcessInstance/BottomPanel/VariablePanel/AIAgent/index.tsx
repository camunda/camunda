/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useState, useEffect} from 'react';
import {Button} from '@carbon/react';
import {View, Download} from '@carbon/react/icons';
import {AIAgentActions, StructuredList, MessageInfo, MessageContent} from './styled';
import {DocumentPreviewModal} from '../Documents/DocumentPreviewModal';

type AIAgentMemory = {
  messages: Array<{
    role: string;
    content: Array<{
      type: string;
      text: string;
    }>;
    toolCalls?: Array<{
      id: string;
      name: string;
      arguments: Record<string, unknown>;
    }>;
    metadata?: {
      timestamp?: string;
      framework?: {
        tokenUsage?: {
          totalTokenCount: number;
          inputTokenCount: number;
          outputTokenCount: number;
        };
      };
    };
    results?: Array<{
      id: string;
      name: string;
      content: string;
    }>;
  }>;
};

const AIAgent: React.FC = () => {
  const [data, setData] = useState<AIAgentMemory | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isJSONModalOpen, setIsJSONModalOpen] = useState(false);

  useEffect(() => {
    fetch('/ai_agent_memory.json')
      .then((res) => res.json())
      .then(setData)
      .catch((err) => console.error('Error loading AI agent memory:', err))
      .finally(() => setIsLoading(false));
  }, []);

  const handleViewJSON = () => {
    setIsJSONModalOpen(true);
  };

  const handleDownload = () => {
    const link = window.document.createElement('a');
    link.href = '/ai_agent_memory.json';
    link.download = 'ai_agent_memory.json';
    window.document.body.appendChild(link);
    link.click();
    window.document.body.removeChild(link);
  };

  if (isLoading) {
    return <div>Loading...</div>;
  }

  if (!data) {
    return <div>Unable to load AI agent memory data.</div>;
  }

  // Extract relevant information for display
  const messages = data.messages || [];
  const rows = messages
    .filter((msg) => msg.role === 'user' || msg.role === 'assistant')
    .map((msg, index) => {
      const content = msg.content?.[0]?.text || '';
      const toolCalls = msg.toolCalls || [];
      const timestamp = msg.metadata?.timestamp || '';
      const tokenUsage = msg.metadata?.framework?.tokenUsage;

      return {
        key: `message-${index}`,
        columns: [
          {
            cellContent: (
              <MessageInfo>
                {msg.role === 'user' ? 'User' : 'Assistant'}
              </MessageInfo>
            ),
            width: '15%',
          },
          {
            cellContent: (
              <MessageContent title={content}>
                {content.length > 100
                  ? `${content.substring(0, 100)}...`
                  : content}
              </MessageContent>
            ),
            width: '40%',
          },
          {
            cellContent: (
              <MessageInfo>
                {toolCalls.length > 0 ? toolCalls.length : '-'}
              </MessageInfo>
            ),
            width: '8%',
          },
          {
            cellContent: (
              <MessageInfo>
                {toolCalls.length > 0 ? toolCalls.map(tc => tc.name).join(', ') : '-'}
              </MessageInfo>
            ),
            width: '12%',
          },
          {
            cellContent: (
              <MessageInfo>
                {tokenUsage?.totalTokenCount
                  ? `${tokenUsage.inputTokenCount}/${tokenUsage.outputTokenCount}`
                  : '-'}
              </MessageInfo>
            ),
            width: '15%',
          },
          {
            cellContent: (
              <MessageInfo>
                {timestamp ? new Date(timestamp).toLocaleString() : '-'}
              </MessageInfo>
            ),
            width: '10%',
          },
        ],
      };
    });

  return (
    <>
      <StructuredList
        label="AI Agent Memory"
        headerColumns={[
          {cellContent: 'Role', width: '10%'},
          {cellContent: 'Content', width: '45 s%'},
          {cellContent: 'Tool Calls', width: '10%'},
          {cellContent: 'Tools Used', width: '10%'},
          {cellContent: 'Tokens (I/O)', width: '10%'},
          {cellContent: 'Timestamp', width: '15%'},
        ]}
        headerSize="sm"
        verticalCellPadding="var(--cds-spacing-02)"
        rows={rows}
        dataTestId="ai-agent-memory-table"
      />
      <AIAgentActions>
        <Button
          kind="ghost"
          size="md"
          onClick={handleViewJSON}
          renderIcon={View}
        >
          View JSON
        </Button>
        <Button
          kind="ghost"
          size="md"
          onClick={handleDownload}
          renderIcon={Download}
        >
          Download
        </Button>
      </AIAgentActions>
      {isJSONModalOpen && (
        <DocumentPreviewModal
          document={{
            id: 'ai-agent-memory',
            name: 'ai_agent_memory.json',
            type: 'application/json',
            size: data ? JSON.stringify(data).length : 0,
            path: '/ai_agent_memory.json',
          }}
          isOpen={isJSONModalOpen}
          onClose={() => setIsJSONModalOpen(false)}
        />
      )}
    </>
  );
};

export {AIAgent};
