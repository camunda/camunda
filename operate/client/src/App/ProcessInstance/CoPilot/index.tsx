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
  'Walk me through what happened',
  'Why does this instance have an incident?',
  'How can I resolve this incident?',
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
  'Explain this error: EXTRACT_VALUE_ERROR on gateway "Route by Flow Type".';

const INCIDENT_EXPLANATION_MESSAGE =
  'The error EXTRACT_VALUE_ERROR means the FEEL expression on gateway "Route by Flow Type" could not be evaluated — it returned NULL instead of the expected BOOLEAN.\n\n' +
  '### Error details\n' +
  '• Error type: Expression evaluation failure\n' +
  '• Affected element: Route by Flow Type (exclusive gateway)\n' +
  '• Expression: `list contains(flows,"2")`\n' +
  '• Expected type: BOOLEAN\n' +
  '• Actual result: NULL\n\n' +
  '### Evaluation warnings\n' +
  '• NO_VARIABLE_FOUND: No variable found with name `flows`\n' +
  '• FUNCTION_INVOCATION_FAILURE: Failed to invoke `list contains` — illegal arguments: null, "2"\n\n' +
  'This indicates that `flows` was not in scope when the gateway was reached. The function received null as its first argument and could not return a valid boolean result.';

const INCIDENT_RESOLUTION_MESSAGE =
  '### Recommended steps\n' +
  '1. Check that `flows` is set before this gateway — verify the upstream task or event that should populate the variable actually ran and produced a value.\n' +
  '2. Confirm the variable name — FEEL expressions are case-sensitive; ensure the variable is named exactly `flows` and not `flow`, `Flows`, or similar.\n' +
  '3. Add a null guard to the expression — consider using `flows != null and list contains(flows,"2")` to prevent NULL propagation if the variable may sometimes be absent.\n' +
  '4. Retry the incident — once `flows` is correctly in scope, use the button below to retry the gateway evaluation.\n' +
  '5. Review the process model — if `flows` should always be set by this point, the upstream task that populates it may itself be failing silently.';

const RETRY_SUGGESTED_ACTIONS: SuggestedAction[] = [
  {label: 'Retry', icon: RetryFailed},
];

// ─── Simulated response: Incidents by error message (dashboard) ──────────────

const INCIDENTS_BY_ERROR_MOCK_PROMPT =
  'Explain the top process incidents by error message.';

const INCIDENTS_BY_ERROR_MESSAGE =
  'Here is a breakdown of the current incidents grouped by error message:\n\n' +
  '### Active error messages\n' +
  '1. Connection timeout — unable to reach https://payments.internal/api/v2/charge — 47 instances across Order Fulfillment (32) and Payment Processing (15)\n' +
  '2. NullPointerException in CustomerOnboardingConnector — 12 instances in Customer Onboarding\n' +
  '3. Job worker unavailable — type: invoice-generator — 8 instances in Invoice Generation\n\n' +
  'Errors 1 and 3 both started spiking approximately 2 hours ago and are likely related to the same infrastructure or deployment change. Error 2 is a recurring pattern — 12 instances is within its usual range and is not a new spike.';

// Sentinel returned by getProcessInstanceResponse to signal a two-message walk-through
const WALK_THROUGH_SENTINEL = '__WALK_THROUGH__';

// ─── Shared audit log content (reused across walk-through and instance analysis) ──

const WALK_THROUGH_PART_1 =
  '### Overview\n' +
  'Started: March 5, 2026 at 09:14 AM\n' +
  'Status: Incident — waiting for manual intervention\n\n' +
  '### Execution path\n' +
  '1. Start Event — completed\n' +
  '2. Validate Request — completed (0.2s)\n' +
  '3. Enrich Request Data — completed (1.4s)\n' +
  '4. Route by Flow Type — failed (EXTRACT_VALUE_ERROR: `flows` not in scope)';

const WALK_THROUGH_PART_2 =
  '### User operations\n' +
  '>>> 09:15:02  anna@example.com  Modified variable `flows` | Set to: ["1","3"]\n' +
  '>>> 09:16:30  anna@example.com  Retried incident on "Route by Flow Type" | Outcome: incident persisted\n\n' +
  '### Variables at time of failure\n' +
  '• `requestId`: "REQ-2024-4491"\n' +
  '• `customerId`: "CUST-4472"\n' +
  '• `requestType`: "TRANSFER"\n' +
  '• `flows`: null\n\n' +
  'The process completed 3 steps successfully before failing at the routing gateway. The `flows` variable was null at that point, which prevented the FEEL expression from evaluating. Total time before incident: 1 minute 46 seconds.';

// ─── Simulated response: Instance analysis from dashboard ────────────────────

// TODO: Replace with actual copilot API call — send processInstanceId, fetch audit log + incidents
function getAnalyzeInstanceResponse(instanceId: string | null): string {
  return (
    `I found an active incident on process instance ${instanceId ?? 'unknown'}.\n\n` +
    '### Execution path\n' +
    '1. Start Event — completed\n' +
    '2. Validate Request — completed (0.2s)\n' +
    '3. Enrich Request Data — completed (1.4s)\n' +
    '4. Route by Flow Type — failed (EXTRACT_VALUE_ERROR: `flows` not in scope)\n\n' +
    '### User operations\n' +
    '>>> 09:15:02  anna@example.com  Modified variable `flows` | Set to: ["1","3"]\n' +
    '>>> 09:16:30  anna@example.com  Retried incident on "Route by Flow Type" | Outcome: incident persisted\n\n' +
    'The variable was set at 09:15 but the retry at 09:16 still failed — the value ["1","3"] may not satisfy the expression, or `flows` is being overwritten or lost before the gateway is re-evaluated.\n\n' +
    'Open the instance to inspect the current variable scope or retry the incident after correcting `flows`.'
  );
}

// ─── Simulated responses: Process Instance context ───────────────────────────

// TODO: Replace with actual copilot API call — send processInstanceId + user message
function getProcessInstanceResponse(input: string): AIResponse {
  const lower = input.toLowerCase();

  if (lower === 'retry') {
    return {
      text:
        'Retry triggered. The incident on "Route by Flow Type" has been re-queued.\n\n' +
        'If `flows` is now correctly set in scope, the gateway expression will be re-evaluated and the instance will continue. A new incident will appear here if the expression still cannot be resolved.',
    };
  }

  if (
    lower.includes('why') ||
    (lower.includes('incident') &&
      (lower.includes('what') || lower.includes('wrong') || lower.includes('cause')))
  ) {
    return {
      text:
        'This instance has an active incident on the exclusive gateway "Route by Flow Type". The error is EXTRACT_VALUE_ERROR — the FEEL expression `list contains(flows,"2")` returned NULL instead of BOOLEAN.\n\n' +
        '### Error details\n' +
        '• Error type: Expression evaluation failure\n' +
        '• Affected element: Route by Flow Type (exclusive gateway)\n' +
        '• Expression: `list contains(flows,"2")`\n' +
        '• Cause: Variable `flows` was not found in scope — `list contains` received null as its first argument\n\n' +
        'This type of error occurs when a required variable is missing at the point where a FEEL expression is evaluated on a gateway.',
    };
  }

  if (
    lower.includes('resolve') ||
    lower.includes('fix') ||
    lower.includes('how can')
  ) {
    return {
      text:
        '### Recommended steps\n' +
        '1. Check that `flows` is set before this gateway — verify the upstream task that should populate the variable actually ran and produced a value.\n' +
        '2. Confirm the variable name — FEEL is case-sensitive; ensure the variable is named exactly `flows` and not `flow`, `Flows`, or similar.\n' +
        '3. Add a null guard to the expression — consider `flows != null and list contains(flows,"2")` to avoid NULL propagation if the variable may sometimes be absent.\n' +
        '4. Retry the incident — once `flows` is correctly in scope, use the button below to retry the gateway evaluation.\n' +
        '5. Review the process model — if `flows` should always be set by this point, the upstream task that populates it may itself be failing silently.\n\n' +
        'Would you like me to check the current variable values or help you update `flows` directly?',
      suggestedActions: RETRY_SUGGESTED_ACTIONS,
    };
  }

  // Walk-through is handled as a two-message sequence in onSubmit — see WALK_THROUGH_SENTINEL
  if (
    lower.includes('walk') ||
    lower.includes('happened') ||
    lower.includes('history') ||
    lower.includes('summary') ||
    lower.includes('through')
  ) {
    return {text: WALK_THROUGH_SENTINEL};
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
        '### Current variables\n' +
        '• `requestId`: "REQ-2024-4491"\n' +
        '• `customerId`: "CUST-4472"\n' +
        '• `requestType`: "TRANSFER"\n' +
        '• `flows`: null\n\n' +
        'The key variable here is `flows` — it is currently null, which is why the gateway expression `list contains(flows,"2")` cannot be evaluated. You can edit it directly in the Variables panel at the bottom of this page.',
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
        '### 1. Payment service worker failures · High severity\n' +
        '47 job failures across 3 processes in the last 2 hours — 8x above the hourly average. Likely a shared connectivity issue with the payment gateway.\n\n' +
        '### 2. Long-running user tasks in Customer Onboarding · Medium severity\n' +
        '12 instances have been waiting at a user task for over 48 hours. This may indicate a staffing gap or an assignment issue.\n\n' +
        '### 3. Rising incident rate in Order Fulfillment · Medium severity\n' +
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
        'Here is a week-over-week comparison (March 3–9 vs February 24 – March 2):\n\n' +
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
        <CodeSnippet key={key} type="inline">
          {value.slice(1, -1)}
        </CodeSnippet>,
      );
    } else {
      parts.push(
        <CodeSnippet key={key} type="inline">
          {value}
        </CodeSnippet>,
      );
    }

    lastIndex = match.index + value.length;
  }

  if (lastIndex < text.length) {
    parts.push(text.slice(lastIndex));
  }

  return <>{parts}</>;
}

const SECTION_HEADER = /^###\s+/;
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
          {entries.map((entry, idx) => {
            // Parse "09:15:02  anna@example.com  changed `paymentGatewayUrl` ..."
            const parts = entry.split(/  +/);
            const time = parts[0] ?? '';
            const actor = parts[1] ?? '';
            const action = parts.slice(2).join(' ');
            const actionLines = action.split('|').map((s) => s.trim());
            return (
              <Styled.AuditEntry key={idx}>
                <Styled.AuditEntryMeta>
                  {time} · {actor}
                </Styled.AuditEntryMeta>
                <Styled.AuditEntryAction>
                  {renderInline(actionLines[0] ?? '', `audit-${blockKey}-${idx}-0`)}
                </Styled.AuditEntryAction>
                {actionLines.slice(1).map((detail, didx) => (
                  <Styled.AuditEntryDetail key={didx}>
                    {renderInline(detail, `audit-${blockKey}-${idx}-d${didx}`)}
                  </Styled.AuditEntryDetail>
                ))}
              </Styled.AuditEntry>
            );
          })}
        </Styled.MessageContentWrapper>,
      );
      continue;
    }

    // Section heading (### )
    if (SECTION_HEADER.test(line)) {
      const headerText = line.replace(SECTION_HEADER, '');
      blocks.push(
        <Styled.MessageSectionHeader key={blockKey++}>
          {renderInline(headerText, `h-${blockKey}`)}
        </Styled.MessageSectionHeader>,
      );
      i++;
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

  // Trigger mock incidents-by-error analysis when opened from the dashboard
  useEffect(() => {
    if (!copilotStore.analyzeIncidentsByErrorMode) return;

    copilotStore.clearAnalyzeIncidentsByErrorMode();
    setMessages([
      {id: 'iby-err-0', type: 'human', text: INCIDENTS_BY_ERROR_MOCK_PROMPT},
    ]);
    setIsProcessing(true);

    // TODO: Replace with actual API call — fetch incidents grouped by error message
    const t = setTimeout(() => {
      setMessages((prev) => [
        ...prev,
        {id: 'iby-err-1', type: 'ai', text: INCIDENTS_BY_ERROR_MESSAGE},
      ]);
      setIsProcessing(false);
    }, 1800);

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
      const response = getResponse(textPrompt);

      if (response.text === WALK_THROUGH_SENTINEL) {
        // Two-message sequence with staggered delays, matching the incident explanation UX
        const t1 = setTimeout(() => {
          addMessage({type: 'ai', text: WALK_THROUGH_PART_1});
        }, 2000);
        const t2 = setTimeout(() => {
          addMessage({
            type: 'ai',
            text: WALK_THROUGH_PART_2,
            suggestedActions: RETRY_SUGGESTED_ACTIONS,
          });
          setIsProcessing(false);
        }, 4500);
        cancelRef.current = () => {
          clearTimeout(t1);
          clearTimeout(t2);
        };
      } else {
        const timeoutId = setTimeout(() => {
          addMessage({
            type: 'ai',
            text: response.text,
            suggestedActions: response.suggestedActions,
          });
          setIsProcessing(false);
        }, 1000);
        cancelRef.current = () => clearTimeout(timeoutId);
      }
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
      placeholder={
        variant === 'chat' ? 'Ask a follow-up question...' : placeholder
      }
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
