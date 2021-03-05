/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useRef, useEffect} from 'react';

import {PANEL_POSITION} from 'modules/constants';
import usePrevious from 'modules/hooks/usePrevious';
import Panel from 'modules/components/Panel';
import * as Styled from './styled';
import {DIRECTION} from '../../constants';

const TRANSITION_TIMEOUT = 200;

type Props = {
  label: string;
  panelPosition: 'RIGHT' | 'LEFT';
  header?: React.ReactNode;
  renderFooter?: (...args: any[]) => any;
  isOverlay?: boolean;
  toggle: (...args: any[]) => any;
  isCollapsed: boolean;
  children?: React.ReactNode;
  verticalLabelOffset: number;
  hasBackgroundColor?: boolean;
  scrollable?: boolean;
  maxWidth?: number;
  onScroll?: (event: React.UIEvent<HTMLDivElement, UIEvent>) => void;
  renderHeader: () => string;
};

function CollapsablePanel({
  label,
  panelPosition,
  header,
  renderFooter,
  isOverlay,
  children,
  isCollapsed,
  toggle,
  verticalLabelOffset,
  hasBackgroundColor,
  scrollable,
  onScroll,
  ...props
}: Props) {
  const buttonDirection =
    panelPosition === PANEL_POSITION.RIGHT ? DIRECTION.RIGHT : DIRECTION.LEFT;

  const expandButtonRef = useRef<HTMLButtonElement>(null);
  const collapseButtonRef = useRef<HTMLButtonElement>(null);

  const prevIsCollapsed = usePrevious(isCollapsed);

  useEffect(() => {
    if (prevIsCollapsed !== isCollapsed) {
      if (isCollapsed) {
        setTimeout(() => expandButtonRef.current?.focus(), TRANSITION_TIMEOUT);
      } else {
        setTimeout(
          () => collapseButtonRef.current?.focus(),
          TRANSITION_TIMEOUT
        );
      }
    }
  }, [isCollapsed, prevIsCollapsed]);
  return (
    <Styled.Collapsable
      {...props}
      isCollapsed={isCollapsed}
      panelPosition={panelPosition}
      isOverlay={isOverlay}
    >
      <Styled.CollapsedPanel
        isCollapsed={isCollapsed}
        transitionTimeout={TRANSITION_TIMEOUT}
        data-testid="collapsed-panel"
      >
        <Styled.ExpandButton
          ref={expandButtonRef}
          title={`Expand ${label}`}
          onClick={toggle}
          data-testid="expand-button"
        >
          <Styled.Vertical offset={verticalLabelOffset}>
            <span>{label}</span>
            {header}
          </Styled.Vertical>
        </Styled.ExpandButton>
      </Styled.CollapsedPanel>

      <Styled.ExpandedPanel
        isCollapsed={isCollapsed}
        panelPosition={panelPosition}
        hasBackgroundColor={hasBackgroundColor}
        transitionTimeout={TRANSITION_TIMEOUT}
        data-testid="expanded-panel"
      >
        <Styled.Header panelPosition={panelPosition}>
          <Styled.CollapseButton
            ref={collapseButtonRef}
            direction={buttonDirection}
            title={`Collapse ${label}`}
            onClick={toggle}
            data-testid="collapse-button"
          />
          {label}
          {header}
        </Styled.Header>
        <Panel.Body scrollable={scrollable} onScroll={onScroll}>
          {children}
        </Panel.Body>
        {renderFooter ? <Panel.Footer>{renderFooter()}</Panel.Footer> : ''}
      </Styled.ExpandedPanel>
    </Styled.Collapsable>
  );
}

CollapsablePanel.defaultProps = {
  renderHeader: () => '',
  isOverlay: false,
  verticalLabelOffset: 0,
};

export default CollapsablePanel;
