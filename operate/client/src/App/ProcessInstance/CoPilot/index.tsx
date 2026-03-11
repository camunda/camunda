/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useRef, useState} from 'react';
import {useLocation} from 'react-router-dom';
import {Button, CodeSnippet, Layer, Stack, Tag} from '@carbon/react';
import {ArrowUp, Close, RetryFailed, StopFilledAlt} from '@carbon/react/icons';
import AISparkle from 'modules/components/Icon/ai-sparkle.svg?react';
import {copilotStore} from 'modules/stores/copilot';
import * as Styled from './styled';

// ─── Types ────────────────────────────────────────────────────────────────────

interface SuggestedAction {
  label: string;
  icon: React.ComponentType<{size?: number}>;
}

interface Message {
  id: string;
  type: 'human' | 'ai' | 'error';
  text: string;
  suggestedActions?: SuggestedAction[];
}

type AIResponse = {
  text: string;
  suggestedActions?: SuggestedAction[];
};

type Props = {
  onClose: () => void;
};

type CopilotContext = 'process-instance' | 'operate';

// ─── Context detection ────────────────────────────────────────────────────────

function useContext(): CopilotContext {
  const {pathname} = useLocation();
  const segments = pathname.split('/').filter(Boolean);
  // /processes/:processInstanceId → process-instance
  return segments[0] === 'processes' && segments.length >= 2
    ? 'process-instance'
    : 'operate';
}

// ─── Suggestion prompts ───────────────────────────────────────────────────────

const PROCESS_INSTANCE_PROMPTS = [
  'Why does this instance have an incident?',
  'How can I resolve this incident?',
  'Walk me through what happened',
];

const OPERATE_PROMPTS = [
  'Which processes have the most incidents?',
  'Are there any anomalies right now?',
  'Compare this week vs last week',
];

// ─── Placeholder text ─────────────────────────────────────────────────────────

const PLACEHOLDER: Record<CopilotContext, string> = {
  'process-instance':
    'Ask about this process instance, or try a suggestion below...',
  operate: 'Ask about your processes and incidents, or try a suggestion below...',
};

// ─── Mock incident explanation (triggered from "Explain with Copilot") ────────

const INCIDENT_MOCK_PROMPT =
  'Explain the active incidents on this process instance.';

const INCIDENT_EXPLANATION_MESSAGE =
  'I found an active incident on this process instance. The error is JOB_NO_RETRIES — the service task "Process Payment" exhausted all 3 retry attempts.\n\n' +
  'Error details:\n' +
  '• Error type: Job worker failure\n' +
  '• Affected element: Process Payment (service task)\n' +
  '• Job type: io.camunda.connector.http.outbound\n' +
  '• Error message: Connection timeout — unable to reach https://payments.internal/api/v2/charge\n\n' +
  'This typically indicates that the external payment service is unreachable or returned an unexpected response.';

const INCIDENT_RESOLUTION_MESSAGE =
  'Here are the recommended next steps:\n\n' +
  '1. Verify the payment service is running — confirm the service at payments.internal is accessible from your job worker environment.\n' +
  '2. Check the job worker logs — look for connection errors, authentication failures, or timeouts.\n' +
  '3. Review the `paymentGatewayUrl` variable — the current value may point to an incorrect or outdated endpoint.\n' +
  '4. Retry the incident — once the service is back online, use the button below to retry directly from here.\n' +
  '5. Increase the retry count — if this endpoint has transient failures, more retries in your BPMN model can improve resilience.';

const RETRY_SUGGESTED_ACTIONS: SuggestedAction[] = [
  {label: 'Retry', icon: RetryFailed},
];

// ─── Simulated response: Instance analysis from dashboard ────────────────────

// TODO: Replace with actual copilot API call — send processInstanceId, fetch audit log + incidents
function getAnalyzeInstanceResponse(instanceId: string | null): string {
  return (
    `I found an active incident on process instance ${instanceId ?? 'unknown'}.\n\n` +
    'Execution path:\n' +
    '1. Start Event — completed\n' +
    '2. Validate Order — completed (0.3s)\n' +
    '3. Check Inventory — completed (1.2s)\n' +
    '4. Reserve Stock — completed (0.8s)\n' +
    '5. Process Payment — failed after 3 retries (45s)\n\n' +
    'Operator activity:\n' +
    '>>> 09:15:02  anna@example.com  changed `paymentGatewayUrl` "…/api/v1/charge" → "…/api/v2/charge"\n' +
    '>>> 09:16:30  anna@example.com  retried "Process Payment" — incident persisted\n' +
    '>>> 09:17:45  system  instance moved to INCIDENT state after max retries exceeded\n\n' +
    'The variable change at 09:15 suggests the endpoint was being updated, but the retry at 09:16 still failed — the payment service may still be unreachable.\n\n' +
    'Open the instance to retry the incident or investigate the variables in detail.'
  );
}

// ─── Simulated responses: Process Instance context ───────────────────────────

// TODO: Replace with actual copilot API call — send processInstanceId + user message
function getProcessInstanceResponse(input: string): AIResponse {
  const lower = input.toLowerCase();

  if (lower === 'retry') {
    return {
      text:
        'Retry triggered. "Process Payment" has been re-queued with 1 retry attempt.\n\n' +
        'If the payment service is now reachable, the instance will resume automatically. A new incident will appear here if the job fails again.',
    };
  }

  if (
    lower.includes('why') ||
    (lower.includes('incident') &&
      (lower.includes('what') || lower.includes('wrong') || lower.includes('cause')))
  ) {
    return {
      text:
        'This instance has an active incident on the service task "Process Payment". The error is JOB_NO_RETRIES — the job worker exhausted all 3 retry attempts.\n\n' +
        'Error details:\n' +
        '• Error type: Job worker failure\n' +
        '• Affected element: Process Payment (service task)\n' +
        '• Job type: io.camunda.connector.http.outbound\n' +
        '• Error message: Connection timeout — unable to reach https://payments.internal/api/v2/charge\n\n' +
        'This type of error indicates that the external payment service is unreachable. The worker retried 3 times over 45 seconds before giving up.',
    };
  }

  if (
    lower.includes('resolve') ||
    lower.includes('fix') ||
    lower.includes('how can')
  ) {
    return {
      text:
        'Based on the incident details, here are the recommended next steps:\n\n' +
        '1. Verify the payment service is running — confirm the service at payments.internal is accessible from your job worker.\n' +
        '2. Check the job worker logs — look for connection errors or authentication failures.\n' +
        '3. Review the `paymentGatewayUrl` variable — the current value may point to an incorrect endpoint.\n' +
        '4. Retry the incident — once the service is back online, use the button below to retry directly from here.\n' +
        '5. Increase the retry count — if this endpoint has transient failures, more retries in your BPMN model can improve resilience.\n\n' +
        'Would you like me to check the worker status or help you update a variable value?',
      suggestedActions: RETRY_SUGGESTED_ACTIONS,
    };
  }

  if (
    lower.includes('walk') ||
    lower.includes('happened') ||
    lower.includes('history') ||
    lower.includes('summary') ||
    lower.includes('through')
  ) {
    return {
      text:
        'Here is a summary of this process execution:\n\n' +
        'Started: March 5, 2026 at 09:14 AM\n' +
        'Status: Incident — waiting for manual intervention\n\n' +
        'Execution path:\n' +
        '1. Start Event — completed\n' +
        '2. Validate Order — completed (0.3s)\n' +
        '3. Check Inventory — completed (1.2s)\n' +
        '4. Reserve Stock — completed (0.8s)\n' +
        '5. Process Payment — failed after 3 retries (45s)\n\n' +
        'Operator activity:\n' +
        '>>> 09:15:02  anna@example.com  changed `paymentGatewayUrl` "…/api/v1/charge" → "…/api/v2/charge"\n' +
        '>>> 09:16:30  anna@example.com  retried "Process Payment" — incident persisted\n' +
        '>>> 09:17:45  system  instance moved to INCIDENT state after max retries exceeded\n\n' +
        'Variables at time of failure:\n' +
        '• `orderId`: "ORD-2024-8821"\n' +
        '• `amount`: 149.99\n' +
        '• `currency`: "EUR"\n' +
        '• `customerId`: "CUST-4472"\n\n' +
        'The process ran successfully through 4 steps before failing at the payment task. Total time before incident: 2 minutes 34 seconds.',
    };
  }

  if (lower.includes('state') || lower.includes('status')) {
    return {
      text:
        'This process instance is currently in an INCIDENT state. It has been paused at the "Process Payment" service task since March 5, 2026 at 09:17 AM and is waiting for manual resolution.\n\n' +
        'The instance will resume automatically once the incident is retried successfully.',
      suggestedActions: RETRY_SUGGESTED_ACTIONS,
    };
  }

  if (lower.includes('variable')) {
    return {
      text:
        'Variables set on this instance at the current scope:\n\n' +
        '• `orderId`: "ORD-2024-8821"\n' +
        '• `amount`: 149.99\n' +
        '• `currency`: "EUR"\n' +
        '• `customerId`: "CUST-4472"\n' +
        '• `paymentGatewayUrl`: https://payments.internal/api/v2/charge\n\n' +
        'You can view and edit these in the Variables panel at the bottom of this page. Select a specific flow node to see locally scoped variables.',
    };
  }

  return {
    text:
      'I can help you understand this process instance. Here are some things I can assist with:\n\n' +
      '• Explaining incidents and their root causes\n' +
      '• Suggesting steps to resolve incidents\n' +
      '• Summarising the execution history\n' +
      '• Explaining variables and their values\n\n' +
      'What would you like to know?',
  };
}

// ─── Simulated responses: Operate-level context ───────────────────────────────

// TODO: Replace with actual copilot API call — send aggregated cluster metrics + user message
function getOperateResponse(input: string): AIResponse {
  const lower = input.toLowerCase();

  if (
    lower.includes('most') ||
    lower.includes('top') ||
    lower.includes('which process') ||
    lower.includes('most incident')
  ) {
    return {
      text:
        'Here are the top processes by active incident count today:\n\n' +
        '1. Order Fulfillment — 47 active incidents (up 18% from yesterday)\n' +
        '2. Payment Processing — 23 active incidents (spike started ~2 hours ago)\n' +
        '3. Customer Onboarding — 12 active incidents (stable)\n' +
        '4. Invoice Generation — 8 active incidents (down 40% from yesterday)\n' +
        '5. Inventory Sync — 5 active incidents (stable)\n\n' +
        'The spike in Order Fulfillment and Payment Processing appears correlated — both involve the same payment service worker, suggesting a shared infrastructure issue.',
    };
  }

  if (
    lower.includes('anomal') ||
    lower.includes('unusual') ||
    lower.includes('right now') ||
    lower.includes('current')
  ) {
    return {
      text:
        'I detected 3 potential anomalies in the last 6 hours:\n\n' +
        '1. Payment service worker failures (High severity)\n' +
        '47 job failures across 3 processes in the last 2 hours — 8x above the hourly average. Likely a shared connectivity issue with the payment gateway.\n\n' +
        '2. Long-running user tasks in Customer Onboarding (Medium severity)\n' +
        '12 instances have been waiting at a user task for over 48 hours. This may indicate a staffing gap or an assignment issue.\n\n' +
        '3. Rising incident rate in Order Fulfillment (Medium severity)\n' +
        'Incident rate up 34% compared to the 7-day average. Failure pattern points to connection timeouts with an external service.\n\n' +
        'Would you like to drill into any of these?',
    };
  }

  if (
    lower.includes('week') ||
    lower.includes('compar') ||
    lower.includes('trend') ||
    lower.includes('last')
  ) {
    return {
      text:
        'Week-over-week comparison (March 3–9 vs February 24 – March 2):\n\n' +
        '• Process instances started: 1,842 vs 1,756 (+5%)\n' +
        '• Completed successfully: 1,614 (87.6%) vs 1,689 (96.2%) — down 8.6 percentage points\n' +
        '• Active incidents: 95 vs 31 — up 206%\n' +
        '• Average incident resolution time: 4.2 hours vs 1.8 hours — degraded\n\n' +
        'The drop in completion rate and spike in incidents this week is primarily driven by failures in Order Fulfillment and Payment Processing. Both trends began on March 8 at approximately 11:00 AM, which may coincide with a recent deployment or configuration change.',
    };
  }

  return {
    text:
      'I can help you get a cluster-level overview. Here are some things I can assist with:\n\n' +
      '• Identifying which processes have the most incidents\n' +
      '• Detecting anomalies or unusual patterns\n' +
      '• Comparing metrics across time periods\n' +
      '• Highlighting long-running or stuck instances\n\n' +
      'What would you like to know?',
  };
}

// ─── Message content renderer ─────────────────────────────────────────────────

// Matches triple-backtick code blocks, inline backtick code, and URLs
const INLINE_TOKEN_REGEX = /(```[\s\S]*?```|`[^`\n]+`|https?:\/\/[^\s,)]+)/g;

function renderInline(text: string, keyBase: string): React.ReactNode {
  const parts: React.ReactNode[] = [];
  let lastIndex = 0;
  let counter = 0;

  INLINE_TOKEN_REGEX.lastIndex = 0;
  let match: RegExpExecArray | null;

  while ((match = INLINE_TOKEN_REGEX.exec(text)) !== null) {
    if (match.index > lastIndex) {
      parts.push(text.slice(lastIndex, match.index));
    }

    const value = match[0];
    const key = `${keyBase}-${counter++}`;

    if (value.startsWith('```')) {
      parts.push(
        <Layer key={key}>
          <CodeSnippet type="multi" wrapText>
            {value.slice(3, -3).trimStart()}
          </CodeSnippet>
        </Layer>,
      );
    } else if (value.startsWith('`')) {
      parts.push(
        <Layer key={key}>
          <CodeSnippet type="inline">{value.slice(1, -1)}</CodeSnippet>
        </Layer>,
      );
    } else {
      parts.push(
        <Layer key={key}>
          <CodeSnippet type="inline">{value}</CodeSnippet>
        </Layer>,
      );
    }

    lastIndex = match.index + value.length;
  }

  if (lastIndex < text.length) {
    parts.push(text.slice(lastIndex));
  }

  return <>{parts}</>;
}

const ORDERED_ITEM = /^\d+\.\s+/;
const UNORDERED_ITEM = /^[•\-]\s+/;
const AUDIT_ENTRY = /^>>>\s+/;

const MessageContent: React.FC<{text: string}> = ({text}) => {
  const lines = text.split('\n');
  const blocks: React.ReactNode[] = [];
  let i = 0;
  let blockKey = 0;

  while (i < lines.length) {
    const line = lines[i];

    // Audit log entry
    if (AUDIT_ENTRY.test(line)) {
      const entries: string[] = [];
      while (i < lines.length && AUDIT_ENTRY.test(lines[i])) {
        entries.push(lines[i].replace(AUDIT_ENTRY, ''));
        i++;
      }
      blocks.push(
        <Styled.MessageContentWrapper key={blockKey++}>
          {entries.map((entry, idx) => (
            <Styled.AuditEntry key={idx}>{entry}</Styled.AuditEntry>
          ))}
        </Styled.MessageContentWrapper>,
      );
      continue;
    }

    // Ordered list
    if (ORDERED_ITEM.test(line)) {
      const items: string[] = [];
      while (i < lines.length && ORDERED_ITEM.test(lines[i])) {
        items.push(lines[i].replace(ORDERED_ITEM, ''));
        i++;
      }
      blocks.push(
        <Styled.MessageOrderedList key={blockKey++}>
          {items.map((item, idx) => (
            <li key={idx}>{renderInline(item, `ol-${blockKey}-${idx}`)}</li>
          ))}
        </Styled.MessageOrderedList>,
      );
      continue;
    }

    // Unordered list
    if (UNORDERED_ITEM.test(line)) {
      const items: string[] = [];
      while (i < lines.length && UNORDERED_ITEM.test(lines[i])) {
        items.push(lines[i].replace(UNORDERED_ITEM, ''));
        i++;
      }
      blocks.push(
        <Styled.MessageUnorderedList key={blockKey++}>
          {items.map((item, idx) => (
            <li key={idx}>{renderInline(item, `ul-${blockKey}-${idx}`)}</li>
          ))}
        </Styled.MessageUnorderedList>,
      );
      continue;
    }

    // Empty line — paragraph break, skip
    if (line.trim() === '') {
      i++;
      continue;
    }

    // Paragraph — collect consecutive non-list, non-empty lines
    const paraLines: string[] = [];
    while (
      i < lines.length &&
      lines[i].trim() !== '' &&
      !ORDERED_ITEM.test(lines[i]) &&
      !UNORDERED_ITEM.test(lines[i]) &&
      !AUDIT_ENTRY.test(lines[i])
    ) {
      paraLines.push(lines[i]);
      i++;
    }
    if (paraLines.length > 0) {
      blocks.push(
        <Styled.MessageParagraph key={blockKey++}>
          {renderInline(paraLines.join('\n'), `p-${blockKey}`)}
        </Styled.MessageParagraph>,
      );
    }
  }

  return <Styled.MessageContentWrapper>{blocks}</Styled.MessageContentWrapper>;
};

// ─── Sub-components ───────────────────────────────────────────────────────────

const CopilotIcon: React.FC = () => (
  <Styled.CopilotIconWrapper>
    <AISparkle />
  </Styled.CopilotIconWrapper>
);

const TextAreaAndSubmit: React.FC<{
  text: string;
  placeholder: string;
  onChangeText: (text: string) => void;
  onSubmit: (overrideText?: string) => void;
  onCancel: () => void;
  isProcessing: boolean;
  variant?: 'intro' | 'chat';
}> = ({text, placeholder, onChangeText, onSubmit, onCancel, isProcessing, variant = 'chat'}) => {
  const textboxRef = useRef<HTMLTextAreaElement | null>(null);

  const handleSubmitButtonClick = () => {
    if (isProcessing) {
      onCancel();
    } else {
      onSubmit();
    }
  };

  const onTextareaKeydown = (event: React.KeyboardEvent) => {
    const {code, shiftKey} = event;
    if (code !== 'Enter') return;
    event.preventDefault();
    if (shiftKey) {
      onChangeText(text + '\n');
      setTimeout(() => {
        const textarea = textboxRef.current;
        if (textarea) textarea.scrollTop = textarea.scrollHeight;
      }, 0);
    } else if (!isProcessing) {
      handleSubmitButtonClick();
    }
  };

  useEffect(() => {
    const textarea = textboxRef.current;
    if (textarea) {
      const initialHeight = variant === 'intro' ? 70 : 41;
      textarea.style.height = initialHeight + 'px';
      const newHeight = Math.min(textarea.scrollHeight, Styled.MAX_TEXTAREA_HEIGHT);
      textarea.style.height = newHeight + 'px';
      textarea.style.overflowY =
        newHeight >= Styled.MAX_TEXTAREA_HEIGHT ? 'auto' : 'hidden';
    }
  }, [text, variant]);

  return (
    <Styled.ChatboxForm $variant={variant}>
      <Styled.StyledTextArea
        id="copilot-chatbox-textarea"
        aria-label="copilot-chatbox-textarea"
        value={text}
        $variant={variant}
        placeholder={placeholder}
        onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) =>
          onChangeText(e.target.value)
        }
        onKeyDown={onTextareaKeydown}
        ref={textboxRef}
      />
      <Styled.ButtonWrapper>
        <Button
          hasIconOnly
          aria-label={isProcessing ? 'Cancel' : 'Send'}
          iconDescription={isProcessing ? 'Cancel' : 'Send'}
          size="sm"
          kind="ghost"
          renderIcon={() =>
            isProcessing ? (
              <StopFilledAlt />
            ) : (
              <ArrowUp width="16px" height="16px" />
            )
          }
          onClick={handleSubmitButtonClick}
        />
      </Styled.ButtonWrapper>
    </Styled.ChatboxForm>
  );
};

const MessageBubble: React.FC<{
  type: 'human' | 'ai' | 'error';
  isProcessing?: boolean;
  children: React.ReactNode;
}> = ({type, isProcessing, children}) => (
  <Styled.MessageContainer $type={type === 'error' ? 'ai' : type}>
    <Styled.ChatMessageBubble
      $type={type === 'error' ? 'ai' : type}
      $isProcessing={isProcessing}
    >
      {children}
    </Styled.ChatMessageBubble>
  </Styled.MessageContainer>
);

// ─── Main component ───────────────────────────────────────────────────────────

const CoPilot: React.FC<Props> = ({onClose}) => {
  const context = useContext();
  const prompts =
    context === 'process-instance' ? PROCESS_INSTANCE_PROMPTS : OPERATE_PROMPTS;
  const placeholder = PLACEHOLDER[context];
  const getResponse =
    context === 'process-instance' ? getProcessInstanceResponse : getOperateResponse;

  const [messages, setMessages] = useState<Message[]>([]);
  const [text, setText] = useState('');
  const [isProcessing, setIsProcessing] = useState(false);
  const messagesBodyRef = useRef<HTMLDivElement | null>(null);
  const chatboxBodyRef = useRef<HTMLDivElement | null>(null);
  const cancelRef = useRef<(() => void) | null>(null);
  const idCounter = useRef(0);

  const nextId = () => {
    idCounter.current += 1;
    return `msg-${idCounter.current}`;
  };

  const addMessage = useCallback((message: Omit<Message, 'id'>) => {
    setMessages((prev) => [...prev, {id: nextId(), ...message}]);
  }, []);

  // Auto-scroll on new messages
  useEffect(() => {
    const el = messagesBodyRef.current ?? chatboxBodyRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [messages]);

  // Trigger mock instance analysis when opened from the dashboard
  useEffect(() => {
    if (!copilotStore.analyzeInstanceMode) return;

    const instanceId = copilotStore.analyzedInstanceId;
    copilotStore.clearAnalyzeInstanceMode();
    setMessages([
      {
        id: 'analyze-0',
        type: 'human',
        text: `Analyze process instance ${instanceId}`,
      },
    ]);
    setIsProcessing(true);

    // TODO: Replace with actual API call — fetch incidents + audit log for instanceId
    const t = setTimeout(() => {
      setMessages((prev) => [
        ...prev,
        {
          id: 'analyze-1',
          type: 'ai',
          text: getAnalyzeInstanceResponse(instanceId),
          suggestedActions: RETRY_SUGGESTED_ACTIONS,
        },
      ]);
      setIsProcessing(false);
    }, 2000);

    return () => clearTimeout(t);
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // Trigger mock incident explanation when opened from the incidents panel
  useEffect(() => {
    if (!copilotStore.incidentExplanationMode) return;

    copilotStore.clearIncidentExplanationMode();
    setMessages([{id: 'mock-0', type: 'human', text: INCIDENT_MOCK_PROMPT}]);
    setIsProcessing(true);

    // TODO: Replace with actual incident analysis API call — send incident ID + processInstanceId
    const t1 = setTimeout(() => {
      setMessages((prev) => [
        ...prev,
        {id: 'mock-1', type: 'ai', text: INCIDENT_EXPLANATION_MESSAGE},
      ]);
    }, 1800);

    const t2 = setTimeout(() => {
      setMessages((prev) => [
        ...prev,
        {
          id: 'mock-2',
          type: 'ai',
          text: INCIDENT_RESOLUTION_MESSAGE,
          suggestedActions: RETRY_SUGGESTED_ACTIONS,
        },
      ]);
      setIsProcessing(false);
    }, 3800);

    return () => {
      clearTimeout(t1);
      clearTimeout(t2);
    };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const cancel = useCallback(() => {
    setIsProcessing(false);
    cancelRef.current?.();
  }, []);

  const onSubmit = useCallback(
    (overrideText?: string) => {
      const textPrompt =
        typeof overrideText === 'string' ? overrideText : text;
      if (textPrompt.trim().length === 0) return;

      addMessage({type: 'human', text: textPrompt});
      setIsProcessing(true);
      setText('');

      // TODO: Replace with actual API call to copilot service
      const timeoutId = setTimeout(() => {
        const response = getResponse(textPrompt);
        addMessage({
          type: 'ai',
          text: response.text,
          suggestedActions: response.suggestedActions,
        });
        setIsProcessing(false);
      }, 1000);

      cancelRef.current = () => clearTimeout(timeoutId);
    },
    [text, addMessage, getResponse],
  );

  const handleExamplePromptClick = useCallback(
    (promptText: string) => onSubmit(promptText),
    [onSubmit],
  );

  const textAreaAndSubmit = (variant: 'intro' | 'chat' = 'chat') => (
    <TextAreaAndSubmit
      text={text}
      placeholder={placeholder}
      onChangeText={setText}
      onSubmit={onSubmit}
      onCancel={cancel}
      isProcessing={isProcessing}
      variant={variant}
    />
  );

  return (
    <Styled.ChatboxTile
      role="dialog"
      aria-label="Camunda Copilot"
      data-testid="copilot-sidebar"
    >
      <Styled.ChatboxHeader>
        <Styled.HeaderGroup orientation="horizontal" gap={3}>
          <CopilotIcon />
          <Styled.CamundaCopilotHeader>Copilot</Styled.CamundaCopilotHeader>
          <Tag type="gray" size="sm">
            prototype
          </Tag>
        </Styled.HeaderGroup>
        <Styled.HeaderSpace />
        <Styled.HeaderGroup orientation="horizontal">
          <Styled.CloseButton
            size="sm"
            kind="ghost"
            tooltipPosition="bottom"
            iconDescription="Close"
            hasIconOnly
            onClick={onClose}
            renderIcon={Close}
            data-testid="close-copilot-button"
          />
        </Styled.HeaderGroup>
      </Styled.ChatboxHeader>

      <Styled.ChatboxBody ref={chatboxBodyRef}>
        {messages.length === 0 ? (
          <Styled.IntroContainer>
            <Styled.IntroText>
              <p>
                <CopilotIcon />
                <strong>Camunda Copilot</strong>
              </p>
              <Styled.IntroDescription>
                {context === 'process-instance'
                  ? 'Ask questions about this process instance — understand incidents, trace execution history, and get resolution guidance.'
                  : 'Ask questions about your cluster — surface anomalies, identify high-incident processes, and compare trends over time.'}
              </Styled.IntroDescription>
            </Styled.IntroText>

            {textAreaAndSubmit('intro')}

            <Styled.ActionButtonGroup>
              {prompts.map((prompt) => (
                <Styled.StyledSelectableTag
                  key={prompt}
                  size="md"
                  text={prompt}
                  type="purple"
                  onClick={() => handleExamplePromptClick(prompt)}
                  selected={false}
                />
              ))}
            </Styled.ActionButtonGroup>
          </Styled.IntroContainer>
        ) : (
          <Styled.MessagesContainer>
            <Styled.MessagesBody ref={messagesBodyRef}>
              <Styled.MessagesStack>
                {messages.map((message) => (
                  <Styled.MessageStackItem
                    key={message.id}
                    $type={message.type === 'error' ? 'ai' : message.type}
                  >
                    <div>
                      <MessageBubble type={message.type}>
                        <MessageContent text={message.text} />
                      </MessageBubble>
                      {message.type === 'ai' &&
                        message.suggestedActions &&
                        message.suggestedActions.length > 0 && (
                          <Styled.SuggestedActionsBar>
                            {message.suggestedActions.map((action) => (
                              <Button
                                key={action.label}
                                kind="tertiary"
                                size="sm"
                                renderIcon={action.icon}
                                onClick={() => onSubmit(action.label)}
                              >
                                {action.label}
                              </Button>
                            ))}
                          </Styled.SuggestedActionsBar>
                        )}
                    </div>
                  </Styled.MessageStackItem>
                ))}
                {isProcessing && (
                  <MessageBubble type="ai" isProcessing>
                    <Stack
                      orientation="horizontal"
                      gap={1}
                      style={{alignItems: 'center'}}
                    >
                      <Styled.CopilotInlineLoading data-testid="ai-processing" />
                      <Styled.CopilotInlineLoadingText>
                        Analyzing...
                      </Styled.CopilotInlineLoadingText>
                    </Stack>
                  </MessageBubble>
                )}
              </Styled.MessagesStack>
            </Styled.MessagesBody>
            {textAreaAndSubmit('chat')}
          </Styled.MessagesContainer>
        )}
        <Styled.HeaderSpace />
      </Styled.ChatboxBody>
    </Styled.ChatboxTile>
  );
};

export {CoPilot};
