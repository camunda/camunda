/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {forwardRef, useEffect, useRef} from 'react';
import {Button} from '@carbon/react';
import {ArrowDown, ArrowUp, ChatBot, Code, User} from '@carbon/react/icons';
import type {HistoryStep} from './historySteps';
import {
  ContentsHeader,
  ContentsList,
  ContentsRail,
  ContentsStep,
  HistoryNavigation,
} from './styled';

const ICONS = {USER: User, ASSISTANT: ChatBot, TOOL_RESULT: Code};

type Props = {
  steps: HistoryStep[];
  selectedKey: string;
  position: number;
  total: number;
  moreUnloaded: boolean;
  hasEarlier: boolean;
  hasLater: boolean;
  loading: boolean;
  loadError: boolean;
  onSelect: (key: string) => void;
  onPrevious: () => void;
  onNext: () => void;
};

const HistoryContents = forwardRef<HTMLElement, Props>(function HistoryContents(
  {
    steps,
    selectedKey,
    position,
    total,
    moreUnloaded,
    hasEarlier,
    hasLater,
    loading,
    loadError,
    onSelect,
    onPrevious,
    onNext,
  },
  ref,
) {
  const selectedRef = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    selectedRef.current?.scrollIntoView?.({block: 'nearest'});
  }, [selectedKey]);

  return (
    <ContentsRail
      ref={ref}
      aria-label="History contents"
      data-modal-primary-focus
      tabIndex={-1}
    >
      <ContentsHeader>
        <strong>{`Steps · ${total}${moreUnloaded ? '+' : ''}`}</strong>
        <HistoryNavigation aria-label="Conversation navigation">
          <Button
            kind="ghost"
            size="sm"
            hasIconOnly
            iconDescription="Previous card"
            aria-label="Previous card"
            tooltipPosition="bottom"
            disabled={!hasEarlier || loading}
            onClick={onPrevious}
            renderIcon={ArrowUp}
          />
          <span role="status">
            {loading
              ? 'Loading history…'
              : `${position} / ${total}${moreUnloaded ? '+' : ''}`}
          </span>
          <Button
            kind="ghost"
            size="sm"
            hasIconOnly
            iconDescription="Next card"
            aria-label="Next card"
            tooltipPosition="bottom"
            disabled={!hasLater || loading}
            onClick={onNext}
            renderIcon={ArrowDown}
          />
        </HistoryNavigation>
      </ContentsHeader>
      {loadError && (
        <span role="alert">
          Could not load more history. Use the arrows to retry.
        </span>
      )}
      <ContentsList aria-label="Steps">
        {steps.map((step, index) => {
          const Icon = ICONS[step.role];
          const selected = step.key === selectedKey;
          return (
            <li key={step.key}>
              <ContentsStep
                type="button"
                ref={selected ? selectedRef : undefined}
                $role={step.role}
                aria-current={selected ? 'step' : undefined}
                onClick={() => onSelect(step.key)}
              >
                <span className="number">{index + 1}</span>
                <Icon size={16} aria-hidden />
                <span className="text">
                  <span className="title">{step.title}</span>
                  {step.detail && <span className="detail">{step.detail}</span>}
                </span>
              </ContentsStep>
            </li>
          );
        })}
      </ContentsList>
    </ContentsRail>
  );
});

export {HistoryContents};
