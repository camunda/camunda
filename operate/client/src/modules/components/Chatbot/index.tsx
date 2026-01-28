/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useRef, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {Button, IconButton, Layer, TextArea} from '@carbon/react';
import {ChatBot as ChatBotIcon, Close, Renew, Send, Attachment} from '@carbon/react/icons';
import {
  ChatbotContainer,
  ChatbotToggle,
  ChatHeader,
  ChatInputArea,
  ChatMessages,
  ChatWindow,
  MessageBubble,
  ResizeHandle,
  TypingIndicator,
} from './styled';
import {type Message, type ToolCall, type FileAttachment, useChat} from './useChat';
import type {LLMConfig} from './llmClient';
import type {McpClientConfig} from './mcpClient';
import {chatbotStore} from 'modules/stores/chatbot';
import {observer} from 'mobx-react';
import {ChartRenderer} from './visualizations/ChartRenderer';

type ChatbotProps = {
  /** LLM provider configuration */
  llmConfig: LLMConfig;
  /** MCP gateway configuration */
  mcpConfig: McpClientConfig;
  /** Placeholder text for the input */
  placeholder?: string;
};

const DEFAULT_WIDTH = 400;
const DEFAULT_HEIGHT = 500;
const MIN_WIDTH = 300;
const MIN_HEIGHT = 300;

const Chatbot: React.FC<ChatbotProps> = observer(({
  llmConfig,
  mcpConfig,
  placeholder = 'Ask about your processes...',
}) => {
  const navigate = useNavigate();
  const [isOpen, setIsOpen] = useState(false);
  const [windowSize, setWindowSize] = useState({width: DEFAULT_WIDTH, height: DEFAULT_HEIGHT});
  const [isResizing, setIsResizing] = useState<'top' | 'left' | 'top-left' | null>(null);
  const resizeStartRef = useRef({x: 0, y: 0, width: DEFAULT_WIDTH, height: DEFAULT_HEIGHT});
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [attachedFiles, setAttachedFiles] = useState<FileAttachment[]>([]);

  // Get showToolResults from store
  const showToolResults = chatbotStore.showToolResults;

  const {
    messages,
    input,
    setInput,
    isLoading,
    error,
    sendMessage,
    clearMessages,
  } = useChat({
    llmConfig,
    mcpConfig,
    navigate,
  });

  // File conversion helper
  const convertFileToBase64 = (file: File): Promise<string> => {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => {
        const base64 = reader.result as string;
        // Remove data URL prefix (e.g., "data:application/xml;base64,")
        const base64Content = base64.split(',')[1];
        resolve(base64Content);
      };
      reader.onerror = reject;
      reader.readAsDataURL(file);
    });
  };

  // File upload handler
  const handleFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;

    try {
      const attachments: FileAttachment[] = [];

      for (let i = 0; i < files.length; i++) {
        const file = files[i];

        // Validate file extension
        const validExtensions = ['.bpmn', '.dmn', '.form', '.rpa'];
        const extension = file.name.substring(file.name.lastIndexOf('.')).toLowerCase();

        if (!validExtensions.includes(extension)) {
          console.warn(`Invalid file type: ${file.name}. Only .bpmn, .dmn, .form, and .rpa files are supported.`);
          continue;
        }

        const content = await convertFileToBase64(file);
        attachments.push({
          name: file.name,
          content,
          size: file.size,
          type: file.type,
        });
      }

      setAttachedFiles(prev => [...prev, ...attachments]);
    } catch (err) {
      console.error('Failed to read file(s):', err);
    }

    // Reset file input
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const handleRemoveFile = (index: number) => {
    setAttachedFiles(prev => prev.filter((_, i) => i !== index));
  };

  // Scroll to bottom when new messages arrive
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({behavior: 'smooth'});
  }, [messages]);

  // Focus input when chat opens
  useEffect(() => {
    if (isOpen) {
      inputRef.current?.focus();
    }
  }, [isOpen]);

  // Handle resize mouse events
  const handleResizeStart = useCallback((e: React.MouseEvent, direction: 'top' | 'left' | 'top-left') => {
    e.preventDefault();
    setIsResizing(direction);
    resizeStartRef.current = {
      x: e.clientX,
      y: e.clientY,
      width: windowSize.width,
      height: windowSize.height,
    };
  }, [windowSize]);

  useEffect(() => {
    if (!isResizing) return;

    const handleMouseMove = (e: MouseEvent) => {
      const deltaX = resizeStartRef.current.x - e.clientX;
      const deltaY = resizeStartRef.current.y - e.clientY;

      let newWidth = resizeStartRef.current.width;
      let newHeight = resizeStartRef.current.height;

      if (isResizing === 'left' || isResizing === 'top-left') {
        newWidth = Math.max(MIN_WIDTH, resizeStartRef.current.width + deltaX);
      }
      if (isResizing === 'top' || isResizing === 'top-left') {
        newHeight = Math.max(MIN_HEIGHT, resizeStartRef.current.height + deltaY);
      }

      setWindowSize({width: newWidth, height: newHeight});
    };

    const handleMouseUp = () => {
      setIsResizing(null);
    };

    document.addEventListener('mousemove', handleMouseMove);
    document.addEventListener('mouseup', handleMouseUp);

    return () => {
      document.removeEventListener('mousemove', handleMouseMove);
      document.removeEventListener('mouseup', handleMouseUp);
    };
  }, [isResizing]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if ((input.trim() || attachedFiles.length > 0) && !isLoading) {
      sendMessage(input, attachedFiles.length > 0 ? attachedFiles : undefined);
      setAttachedFiles([]); // Clear attachments after sending
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSubmit(e);
    }
  };

  const toggleChat = () => {
    setIsOpen((prev) => !prev);
  };

  return (
    <ChatbotContainer>
      {isOpen && (
        <Layer>
          <ChatWindow
            role="dialog"
            aria-label="Chatbot"
            aria-modal="true"
            $width={windowSize.width}
            $height={windowSize.height}
          >
            <ResizeHandle
              $position="top"
              onMouseDown={(e) => handleResizeStart(e, 'top')}
              title="Drag to resize height"
            />
            <ResizeHandle
              $position="left"
              onMouseDown={(e) => handleResizeStart(e, 'left')}
              title="Drag to resize width"
            />
            <ResizeHandle
              $position="top-left"
              onMouseDown={(e) => handleResizeStart(e, 'top-left')}
              title="Drag to resize"
            />
            <ChatHeader>
              <div className="header-content">
                <ChatBotIcon size={20} />
                <span>Camunda Assistant</span>
              </div>
              <div className="header-actions">
                <IconButton
                  kind="ghost"
                  size="sm"
                  label="Clear conversation"
                  onClick={clearMessages}
                  disabled={messages.length === 0}
                >
                  <Renew />
                </IconButton>
                <IconButton
                  kind="ghost"
                  size="sm"
                  label="Close chat"
                  onClick={toggleChat}
                >
                  <Close />
                </IconButton>
              </div>
            </ChatHeader>

            <ChatMessages>
              {messages.length === 0 && (
                <div className="welcome-message">
                  <p>
                    👋 Hi! I'm your Camunda Assistant. I can help you with:
                  </p>
                  <ul>
                    <li>Understanding process instances</li>
                    <li>Investigating incidents</li>
                    <li>Querying process data</li>
                    <li>Explaining BPMN elements</li>
                  </ul>
                </div>
              )}
              {messages.map((message: Message) => (
                <MessageBubble
                  key={message.id}
                  $role={message.role}
                  $hasChart={!!message.visualization}
                >
                  <div className="message-content">
                    {message.content}
                  </div>
                  {message.attachments && message.attachments.length > 0 && (
                    <div className="message-attachments">
                      <strong>📎 Attached files:</strong>
                      <ul>
                        {message.attachments.map((file, idx) => (
                          <li key={idx}>
                            {file.name} ({(file.size / 1024).toFixed(1)} KB)
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}
                  {message.visualization && (
                    <ChartRenderer visualization={message.visualization} />
                  )}
                  {showToolResults && message.toolCalls && message.toolCalls.length > 0 && (
                    <div className="tool-calls">
                      {message.toolCalls.map((tool: ToolCall, idx: number) => (
                        <div key={idx} className="tool-call">
                          <span className="tool-name">🔧 {tool.name}</span>
                          {tool.result !== undefined && (
                            <pre className="tool-result">
                              {typeof tool.result === 'string'
                                ? tool.result
                                : JSON.stringify(tool.result, null, 2)}
                            </pre>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </MessageBubble>
              ))}
              {isLoading && (
                <TypingIndicator>
                  <span></span>
                  <span></span>
                  <span></span>
                </TypingIndicator>
              )}
              {error && (
                <div className="error-message">
                  ⚠️ {error}
                </div>
              )}
              <div ref={messagesEndRef} />
            </ChatMessages>

            <ChatInputArea onSubmit={handleSubmit}>
              <TextArea
                ref={inputRef}
                id="chatbot-input"
                labelText=""
                hideLabel
                placeholder={placeholder}
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={handleKeyDown}
                disabled={isLoading}
                rows={2}
              />
              <Button
                type="submit"
                kind="primary"
                size="sm"
                disabled={!input.trim() || isLoading}
                renderIcon={Send}
                iconDescription="Send message"
                hasIconOnly
              />
              <div className="file-upload">
                <input
                  ref={fileInputRef}
                  type="file"
                  id="file-upload"
                  accept=".bpmn,.dmn,.form,.rpa"
                  onChange={handleFileSelect}
                  style={{display: 'none'}}
                />
                <Button
                  kind="secondary"
                  size="sm"
                  onClick={() => fileInputRef.current?.click()}
                  renderIcon={Attachment}
                  iconDescription="Attach file"
                >
                  Attach file
                </Button>
                <div className="attached-files">
                  {attachedFiles.map((file, index) => (
                    <div key={index} className="attached-file">
                      <span className="file-name">{file.name}</span>
                      <Button
                        kind="ghost"
                        size="sm"
                        onClick={() => handleRemoveFile(index)}
                        renderIcon={Close}
                        iconDescription="Remove file"
                        hasIconOnly
                      />
                    </div>
                  ))}
                </div>
              </div>
            </ChatInputArea>
          </ChatWindow>
        </Layer>
      )}

      <ChatbotToggle>
        <Button
          kind="primary"
          size="lg"
          renderIcon={isOpen ? Close : ChatBotIcon}
          iconDescription={isOpen ? 'Close chat' : 'Open chat'}
          hasIconOnly
          onClick={toggleChat}
          aria-expanded={isOpen}
          aria-controls="chatbot-window"
        />
      </ChatbotToggle>
    </ChatbotContainer>
  );
});

export {Chatbot};
