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

type OwnProps = {
  label: string;
  panelPosition: 'RIGHT' | 'LEFT';
  header?: React.ReactNode;
  renderFooter?: (...args: any[]) => any;
  isOverlay?: boolean;
  toggle: (...args: any[]) => any;
  isCollapsed: boolean;
  children?: React.ReactNode;
  verticalLabelOffset?: number;
  hasBackgroundColor?: boolean;
  scrollable?: boolean;
  maxWidth?: number;
  onScroll?: (event: React.UIEvent<HTMLDivElement, UIEvent>) => void;
};

type Props = OwnProps & typeof CollapsablePanel.defaultProps;

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

  const expandButtonRef = useRef(null);
  const collapseButtonRef = useRef(null);

  const prevIsCollapsed = usePrevious(isCollapsed);

  useEffect(() => {
    if (prevIsCollapsed !== isCollapsed) {
      if (isCollapsed) {
        // @ts-expect-error ts-migrate(2339) FIXME: Property 'focus' does not exist on type 'never'.
        setTimeout(() => expandButtonRef.current?.focus(), TRANSITION_TIMEOUT);
      } else {
        setTimeout(
          // @ts-expect-error ts-migrate(2339) FIXME: Property 'focus' does not exist on type 'never'.
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
          // @ts-expect-error ts-migrate(2769) FIXME: Property 'panelPosition' does not exist on type 'I... Remove this comment to see the full error message
          panelPosition={panelPosition}
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
            // @ts-expect-error ts-migrate(2769) FIXME: Property 'isExpanded' does not exist on type 'Intr... Remove this comment to see the full error message
            isExpanded={true}
            title={`Collapse ${label}`}
            onClick={toggle}
            data-testid="collapse-button"
          />
          {label}
          {header}
        </Styled.Header>
        {/* @ts-expect-error ts-migrate(2322) FIXME: Property 'scrollable' does not exist on type 'Intr... Remove this comment to see the full error message */}
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
