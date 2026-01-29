/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  AccordionItem,
  CodeSnippet,
  InlineLoading,
  Tag,
  UnorderedList,
  ListItem,
} from '@carbon/react';
import {useState} from 'react';
import type {
  AgentTimelineItem,
  AgentContextContentPart,
  AgentTimelineToolCall,
} from 'modules/agentContext/types';
import {
  ItemHeader,
  ItemMeta,
  ItemBody,
  TimelineRow,
  TimelineRail,
  TimelineDot,
  RowContent,
} from './styled';

type Props = {
  item: AgentTimelineItem;
  isHeader?: boolean;
  isFirst?: boolean;
  isLast?: boolean;
};

function renderContentParts(content: AgentContextContentPart[]) {
  return (
    <UnorderedList>
      {content.map((c, idx) => (
        <ListItem key={`${c.type}-${idx}`}>
          {c.text ?? c.content ?? ''}
        </ListItem>
      ))}
    </UnorderedList>
  );
}

function renderToolCall(tool: AgentTimelineToolCall) {
  return (
    <>
      <ItemMeta>
        <Tag type="blue">{tool.name}</Tag>
        <span>id: {tool.id}</span>
      </ItemMeta>
      <ItemBody>
        <CodeSnippet type="multi" feedback="Copied to clipboard">
          {tool.arguments}
        </CodeSnippet>
      </ItemBody>

      {!tool.result ? (
        <ItemBody>
          <InlineLoading
            description="Waiting for tool result"
            status="active"
            iconDescription="Waiting for tool result"
          />
        </ItemBody>
      ) : (
        <ItemBody>
          <CodeSnippet type="multi" feedback="Copied to clipboard">
            {tool.result.content}
          </CodeSnippet>
        </ItemBody>
      )}
    </>
  );
}

function renderEmptyMessage() {
  return <ItemMeta>Message has no textual content.</ItemMeta>;
}

const TimelineItem: React.FC<Props> = ({
  item,
  isHeader = false,
  isFirst = false,
  isLast = false,
}) => {
  const [isOpen, setIsOpen] = useState(false);

  const railVariant = isHeader
    ? 'header'
    : item.type === 'STATUS'
      ? 'status'
      : 'default';

  const toolHeaderBadges =
    !isOpen && item.type === 'TOOL_CALL'
      ? item.toolCalls.map((t) => (
          <Tag key={t.id} type="blue" size="sm">
            {t.name}
          </Tag>
        ))
      : null;

  const label = (
    <ItemHeader>
      <div>
        {item.type === 'TOOL_CALL' ? 'Tool call' : item.title}
        {toolHeaderBadges && toolHeaderBadges.length > 0 ? (
          <span
            style={{
              marginLeft: '0.5rem',
              display: 'inline-flex',
              gap: '0.25rem',
            }}
          >
            {toolHeaderBadges}
          </span>
        ) : null}
      </div>
    </ItemHeader>
  );

  return (
    <TimelineRow $isFirst={isFirst} $isLast={isLast}>
      <TimelineRail>
        <TimelineDot $variant={railVariant} />
      </TimelineRail>
      <RowContent>
        <AccordionItem
          title={label}
          onHeadingClick={() => setIsOpen((v) => !v)}
        >
          {item.type === 'AGENT_STATE' && (
            <ItemBody>
              <Tag type="green">{item.state ?? 'Unknown'}</Tag>
            </ItemBody>
          )}

          {item.type === 'TOOL_DEFINITIONS' && (
            <ItemBody>
              <UnorderedList>
                {item.toolDefinitions.map((t) => (
                  <ListItem key={t.name}>
                    <strong>{t.name}</strong>
                    {t.description ? ` — ${t.description}` : ''}
                  </ListItem>
                ))}
              </UnorderedList>
            </ItemBody>
          )}

          {item.type === 'SYSTEM_PROMPT' && (
            <ItemBody>{renderContentParts(item.content)}</ItemBody>
          )}

          {item.type === 'LLM_CALL' && (
            <ItemBody>
              {item.message.content && item.message.content.length > 0
                ? renderContentParts(item.message.content)
                : renderEmptyMessage()}
            </ItemBody>
          )}

          {item.type === 'TOOL_CALL' && (
            <ItemBody>
              {item.toolCalls.map((t) => (
                <div key={t.id}>{renderToolCall(t)}</div>
              ))}
            </ItemBody>
          )}

          {item.type === 'STATUS' && (
            <ItemBody>
              <InlineLoading
                description={item.title}
                status="active"
                iconDescription={item.title}
              />
            </ItemBody>
          )}
        </AccordionItem>
      </RowContent>
    </TimelineRow>
  );
};

export {TimelineItem};
