/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useRef, useEffect} from 'react';

import usePrevious from 'modules/hooks/usePrevious';
import {Panel} from 'modules/components/Panel';
import * as Styled from './styled';

const TRANSITION_TIMEOUT = 200;

type Props = {
  label: string;
  panelPosition: 'RIGHT' | 'LEFT';
  header?: React.ReactNode;
  isOverlay?: boolean;
  toggle: (...args: any[]) => any;
  isCollapsed: boolean;
  children?: React.ReactNode;
  verticalLabelOffset?: number;
  hasBackgroundColor?: boolean;
  scrollable?: boolean;
  maxWidth?: number;
};

const CollapsablePanel = React.forwardRef<HTMLDivElement, Props>(
  (
    {
      label,
      panelPosition,
      header,
      isOverlay = false,
      children,
      isCollapsed,
      toggle,
      verticalLabelOffset = 0,
      hasBackgroundColor,
      scrollable,
      ...props
    },
    ref
  ) => {
    const expandButtonRef = useRef<HTMLButtonElement>(null);
    const collapseButtonRef = useRef<HTMLButtonElement>(null);

    const prevIsCollapsed = usePrevious(isCollapsed);

    useEffect(() => {
      if (prevIsCollapsed !== isCollapsed) {
        if (isCollapsed) {
          setTimeout(
            () => expandButtonRef.current?.focus(),
            TRANSITION_TIMEOUT
          );
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
              direction={panelPosition}
              title={`Collapse ${label}`}
              onClick={toggle}
              data-testid="collapse-button"
            />
            {label}
            {header}
          </Styled.Header>
          <Panel.Body scrollable={scrollable} ref={ref}>
            {children}
          </Panel.Body>
        </Styled.ExpandedPanel>
      </Styled.Collapsable>
    );
  }
);

export default CollapsablePanel;
