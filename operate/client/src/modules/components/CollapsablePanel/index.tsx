/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Title} from 'modules/components/PanelTitle';

import {
  Panel,
  Header,
  Collapsable,
  ExpandIcon,
  CollapseIcon,
  IconButton,
  Content,
} from './styled';
import {Layer} from '@carbon/react';
import React, {forwardRef} from 'react';

type Props = {
  label: string;
  panelPosition: 'RIGHT' | 'LEFT';
  isOverlay?: boolean;
  onToggle: () => void;
  isCollapsed: boolean;
  children?: React.ReactNode;
  footer?: React.ReactNode;
  maxWidth: number;
  scrollable?: boolean;
  collapsablePanelRef?: React.RefObject<HTMLDivElement>;
};

const CollapsablePanel = forwardRef<HTMLDivElement, Props>(
  (
    {
      label,
      panelPosition,
      maxWidth,
      isOverlay = false,
      scrollable = true,
      children,
      footer,
      isCollapsed,
      onToggle,
      collapsablePanelRef,
      ...props
    },
    ref,
  ) => {
    const tooltipAlignment = panelPosition === 'RIGHT' ? 'left' : 'right';

    return (
      <Collapsable
        {...props}
        aria-label={label}
        title={label}
        $isCollapsed={isCollapsed}
        $panelPosition={panelPosition}
        $isOverlay={isOverlay}
        $maxWidth={maxWidth}
        ref={collapsablePanelRef}
      >
        {isCollapsed ? (
          <Panel
            data-testid="collapsed-panel"
            $panelPosition={panelPosition}
            $isClickable
            onClick={onToggle}
          >
            <IconButton
              kind="ghost"
              label={`Expand ${label}`}
              aria-label={`Expand ${label}`}
              align={tooltipAlignment}
              size="sm"
            >
              <ExpandIcon size={20} $panelPosition={panelPosition} />
            </IconButton>
            <Title $isVertical>{label}</Title>
          </Panel>
        ) : (
          <Panel data-testid="expanded-panel" $panelPosition={panelPosition}>
            <Header $panelPosition={panelPosition}>
              <Title>{label}</Title>
              <IconButton
                kind="ghost"
                onClick={onToggle}
                label={`Collapse ${label}`}
                aria-label={`Collapse ${label}`}
                align={tooltipAlignment}
                size="sm"
              >
                <CollapseIcon size={20} $panelPosition={panelPosition} />
              </IconButton>
            </Header>
            <Content ref={ref} $scrollable={scrollable}>
              <Layer>{children}</Layer>
            </Content>
            {footer !== undefined && <>{footer}</>}
          </Panel>
        )}
      </Collapsable>
    );
  },
);

export {CollapsablePanel};
