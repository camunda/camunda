/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button} from '@carbon/react';
import classnames from 'classnames';
import {ReactNode, TransitionEvent, useState} from 'react';
import {RowCollapse, RowExpand} from '@carbon/icons-react';

import {t} from 'translation';

import './CollapsibleContainer.scss';

type SectionState = 'half' | 'maximized' | 'minimized';

interface CollapsibleContainerProps {
  title?: string;
  initialState?: SectionState;
  maxHeight?: number;
  onTransitionEnd?: (currentState: SectionState) => void;
  onCollapse?: (currentState: SectionState, newState: SectionState) => void;
  onExpand?: (currentState: SectionState, newState: SectionState) => void;
  children?: ReactNode;
}

export function CollapsibleContainer({
  title,
  children,
  maxHeight,
  onTransitionEnd,
  onCollapse,
  onExpand,
  initialState = 'half',
}: CollapsibleContainerProps) {
  const [sectionState, setSectionState] = useState<SectionState>(initialState);
  const [showChildren, setShowChildren] = useState(initialState !== 'minimized');

  const handleToggle = (newState: SectionState) => {
    setSectionState(newState);
  };

  const handleTransitionEnd = (evt: TransitionEvent<HTMLDivElement>) => {
    if (evt.propertyName === 'max-height') {
      onTransitionEnd?.(sectionState);
      setShowChildren(sectionState !== 'minimized');
    }
  };

  return (
    <div
      className={classnames('CollapsibleContainer', sectionState)}
      style={{maxHeight: sectionState === 'maximized' ? maxHeight : undefined}}
      onTransitionEnd={handleTransitionEnd}
    >
      <div className="toolbar">
        {title !== undefined && <b>{title}</b>}
        <div className="controls">
          {sectionState !== 'maximized' && (
            <Button
              hasIconOnly
              iconDescription={t('common.expand').toString()}
              kind="ghost"
              onClick={() => {
                const newState = sectionState === 'minimized' ? 'half' : 'maximized';
                onExpand?.(sectionState, newState);
                handleToggle(newState);
                setShowChildren(true);
              }}
              className="expandButton"
              tooltipPosition="left"
              renderIcon={RowCollapse}
            />
          )}
          {sectionState !== 'minimized' && (
            <Button
              hasIconOnly
              iconDescription={t('common.collapse').toString()}
              kind="ghost"
              onClick={() => {
                const newState = sectionState === 'maximized' ? 'half' : 'minimized';
                onCollapse?.(sectionState, newState);
                handleToggle(newState);
              }}
              className="collapseButton"
              tooltipPosition="left"
              renderIcon={RowExpand}
            />
          )}
        </div>
      </div>
      {showChildren && children}
    </div>
  );
}
