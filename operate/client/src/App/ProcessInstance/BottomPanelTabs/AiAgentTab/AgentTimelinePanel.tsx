/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  Checkmark,
  UserAvatar,
  WarningAlt,
  RadioButtonChecked,
  WatsonHealthAiResults,
  Tools,
} from '@carbon/icons-react';
import {Tag} from '@carbon/react';
import type {AgentElementData} from 'modules/mock-server/agentDemoData';
import type {AgentTimelineSelection} from './index';
import {
  SectionLabel,
  IterationRow,
  ToolRow,
  ToolCallList,
  StatusIndicator,
  TimelineLine,
  UserMessageRow,
  UserAvatar as UserAvatarCircle,
} from './styled';

function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('en-US', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  });
}

function getDuration(start: string, end: string): string {
  const ms = new Date(end).getTime() - new Date(start).getTime();
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
}

function getIterationIndicatorStatus(
  toolCalls: AgentElementData['iterations'][0]['toolCalls'],
): 'active' | 'completed' | 'failed' {
  if (toolCalls.some((t) => t.status === 'FAILED')) return 'failed';
  if (toolCalls.some((t) => t.status === 'ACTIVE')) return 'active';
  return 'completed';
}

interface Props {
  agentData: AgentElementData;
  selection: AgentTimelineSelection;
  onSelect: (sel: AgentTimelineSelection) => void;
}

const AgentTimelinePanel: React.FC<Props> = ({
  agentData,
  selection,
  onSelect,
}) => {
  const isIterationSelected = (iterNum: number) =>
    selection?.type === 'iteration' && selection.iterationNumber === iterNum;
  const isToolSelected = (iterNum: number, toolIdx: number) =>
    selection?.type === 'tool' &&
    selection.iterationNumber === iterNum &&
    selection.toolIndex === toolIdx;

  return (
    <div>
      {/* Header */}
      <div style={{marginBottom: 'var(--cds-spacing-05)'}}>
        <IterationRow
          $selected={!selection}
          onClick={() => onSelect(null)}
          aria-label="Show AI Agent overview"
        >
          <WatsonHealthAiResults size={16} />
          <div style={{flex: 1, minWidth: 0}}>
            <div
              style={{
                fontSize: 'var(--cds-heading-compact-01-font-size)',
                fontWeight: 600,
              }}
            >
              AI Agent
            </div>
            <div
              style={{
                fontSize: 'var(--cds-label-01-font-size)',
                color: 'var(--cds-text-secondary)',
                marginTop: 2,
              }}
            >
              {agentData.modelProvider} &middot; {agentData.iterations.length}{' '}
              iteration{agentData.iterations.length !== 1 ? 's' : ''} &middot;{' '}
              {agentData.usage.toolsCalled.current} tool call
              {agentData.usage.toolsCalled.current !== 1 ? 's' : ''}
            </div>
          </div>
        </IterationRow>
      </div>

      {/* Conversation label */}
      <SectionLabel>Conversation</SectionLabel>

      {/* Iterations */}
      <div>
        {agentData.iterations.map((iteration, i) => {
          const isLast = i === agentData.iterations.length - 1;
          const indicatorStatus = getIterationIndicatorStatus(
            iteration.toolCalls,
          );

          return (
            <div key={iteration.iterationNumber}>
              {/* User message — shown on first iteration */}
              {iteration.userMessage && (
                <UserMessageRow>
                  <div
                    style={{
                      display: 'flex',
                      flexDirection: 'column',
                      alignItems: 'center',
                      flexShrink: 0,
                    }}
                  >
                    <UserAvatarCircle>
                      <UserAvatar size={12} />
                    </UserAvatarCircle>
                    <TimelineLine />
                  </div>
                  <div
                    style={{
                      flex: 1,
                      minWidth: 0,
                      paddingBottom: 'var(--cds-spacing-03)',
                    }}
                  >
                    <div
                      style={{
                        fontSize: 10,
                        fontWeight: 600,
                        letterSpacing: '0.32px',
                        textTransform: 'uppercase' as const,
                        color: 'var(--cds-text-secondary)',
                        marginBottom: 2,
                      }}
                    >
                      User
                    </div>
                    <div
                      style={{
                        fontSize: 'var(--cds-body-compact-01-font-size)',
                        lineHeight: '1.5',
                      }}
                    >
                      {iteration.userMessage}
                    </div>
                  </div>
                </UserMessageRow>
              )}

              {/* Agent iteration row */}
              <div style={{display: 'flex', gap: 'var(--cds-spacing-03)'}}>
                <div
                  style={{
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    flexShrink: 0,
                  }}
                >
                  <StatusIndicator $status={indicatorStatus} />
                  {!isLast && <TimelineLine />}
                </div>

                <div
                  style={{
                    flex: 1,
                    minWidth: 0,
                    paddingBottom:
                      iteration.toolCalls.length > 0
                        ? 'var(--cds-spacing-03)'
                        : isLast
                          ? 0
                          : 'var(--cds-spacing-05)',
                  }}
                >
                  <IterationRow
                    $selected={isIterationSelected(iteration.iterationNumber)}
                    onClick={() =>
                      onSelect(
                        isIterationSelected(iteration.iterationNumber)
                          ? null
                          : {
                              type: 'iteration',
                              iterationNumber: iteration.iterationNumber,
                            },
                      )
                    }
                    aria-label={`Agent iteration ${iteration.iterationNumber}`}
                  >
                    <WatsonHealthAiResults
                      size={14}
                      style={{
                        flexShrink: 0,
                        color: 'var(--cds-icon-secondary)',
                      }}
                    />
                    <div style={{flex: 1, minWidth: 0}}>
                      <div
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 'var(--cds-spacing-03)',
                        }}
                      >
                        <span
                          style={{
                            fontSize: 'var(--cds-body-compact-01-font-size)',
                            fontWeight: 600,
                            flex: 1,
                          }}
                        >
                          Agent iteration {iteration.iterationNumber}
                        </span>
                        <span
                          style={{
                            fontSize: 10,
                            color: 'var(--cds-text-secondary)',
                            fontFamily:
                              "var(--cds-code-01-font-family, 'IBM Plex Mono', monospace)",
                            flexShrink: 0,
                          }}
                        >
                          {formatTime(iteration.startTimestamp)}
                        </span>
                        {iteration.endTimestamp && (
                          <span
                            style={{
                              fontSize: 10,
                              color: 'var(--cds-text-secondary)',
                              flexShrink: 0,
                            }}
                          >
                            {getDuration(
                              iteration.startTimestamp,
                              iteration.endTimestamp,
                            )}
                          </span>
                        )}
                      </div>
                      {iteration.agentMessage && (
                        <div
                          style={{
                            marginTop: 2,
                            fontSize: 'var(--cds-label-01-font-size)',
                            color: 'var(--cds-text-secondary)',
                            lineHeight: '1.4',
                            display: '-webkit-box',
                            WebkitLineClamp: 2,
                            WebkitBoxOrient: 'vertical' as const,
                            overflow: 'hidden',
                          }}
                        >
                          {iteration.agentMessage}
                        </div>
                      )}
                    </div>
                  </IterationRow>
                </div>
              </div>

              {/* Tool calls */}
              {iteration.toolCalls.length > 0 && (
                <ToolCallList>
                  {iteration.toolCalls.map((tool, toolIdx) => (
                    <ToolRow
                      key={`${iteration.iterationNumber}-${toolIdx}`}
                      $selected={isToolSelected(
                        iteration.iterationNumber,
                        toolIdx,
                      )}
                      onClick={() =>
                        onSelect(
                          isToolSelected(iteration.iterationNumber, toolIdx)
                            ? null
                            : {
                                type: 'tool',
                                iterationNumber: iteration.iterationNumber,
                                toolIndex: toolIdx,
                              },
                        )
                      }
                      aria-label={`Tool call: ${tool.toolName}`}
                    >
                      <Tools
                        size={14}
                        style={{
                          flexShrink: 0,
                          color:
                            tool.status === 'FAILED'
                              ? 'var(--cds-support-error)'
                              : tool.status === 'ACTIVE'
                                ? 'var(--cds-support-success)'
                                : 'var(--cds-icon-secondary)',
                        }}
                      />
                      <span
                        style={{
                          flex: 1,
                          minWidth: 0,
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap',
                        }}
                      >
                        {tool.toolName}
                      </span>
                      {tool.duration && (
                        <span
                          style={{
                            fontSize: 10,
                            color: 'var(--cds-text-secondary)',
                            flexShrink: 0,
                          }}
                        >
                          {tool.duration}
                        </span>
                      )}
                      {tool.status === 'FAILED' && (
                        <Tag type="red" size="sm">
                          Failed
                        </Tag>
                      )}
                      {tool.status === 'ACTIVE' && (
                        <Tag type="green" size="sm">
                          Running
                        </Tag>
                      )}
                    </ToolRow>
                  ))}
                </ToolCallList>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
};

export {AgentTimelinePanel};
