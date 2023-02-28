/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useRef, forwardRef, useLayoutEffect} from 'react';

import usePrevious from 'modules/hooks/usePrevious';
import {Panel} from 'modules/components/Panel';
import {
  Collapsable,
  CollapsedPanel,
  ExpandButton,
  Vertical,
  ExpandedPanel,
  Header,
  CollapseButton,
} from './styled';

const TRANSITION_TIMEOUT = 200;

type Props = {
  label: string;
  panelPosition: 'RIGHT' | 'LEFT';
  header?: React.ReactNode;
  isOverlay?: boolean;
  toggle: () => void;
  isCollapsed: boolean;
  children?: React.ReactNode;
  verticalLabelOffset?: number;
  hasBackgroundColor?: boolean;
  scrollable?: boolean;
  maxWidth?: number;
  collapsablePanelRef?: React.RefObject<HTMLDivElement>;
};

const CollapsablePanel = forwardRef<HTMLDivElement, Props>(
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
      collapsablePanelRef,
      ...props
    },
    ref
  ) => {
    const expandButtonRef = useRef<HTMLButtonElement>(null);
    const collapseButtonRef = useRef<HTMLButtonElement>(null);
    const prevIsCollapsed = usePrevious(isCollapsed);

    useLayoutEffect(() => {
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
      <Collapsable
        {...props}
        isCollapsed={isCollapsed}
        panelPosition={panelPosition}
        isOverlay={isOverlay}
        transitionTimeout={TRANSITION_TIMEOUT}
        ref={collapsablePanelRef}
      >
        {isCollapsed ? (
          <CollapsedPanel data-testid="collapsed-panel">
            <ExpandButton
              ref={expandButtonRef}
              title={`Expand ${label}`}
              aria-label={`Expand ${label}`}
              onClick={toggle}
              data-testid="expand-button"
            >
              <Vertical offset={verticalLabelOffset}>
                <span>{label}</span>
                {header}
              </Vertical>
            </ExpandButton>
          </CollapsedPanel>
        ) : (
          <ExpandedPanel
            panelPosition={panelPosition}
            hasBackgroundColor={hasBackgroundColor}
            data-testid="expanded-panel"
          >
            <Header panelPosition={panelPosition}>
              <CollapseButton
                ref={collapseButtonRef}
                direction={panelPosition}
                title={`Collapse ${label}`}
                onClick={toggle}
                data-testid="collapse-button"
              />
              {label}
              {header}
            </Header>
            <Panel.Body scrollable={scrollable} ref={ref}>
              {children}
            </Panel.Body>
          </ExpandedPanel>
        )}
      </Collapsable>
    );
  }
);

export {CollapsablePanel};
