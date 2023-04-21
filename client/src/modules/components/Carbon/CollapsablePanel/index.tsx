/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Title} from 'modules/components/Carbon/PanelTitle';

import {
  Panel,
  Header,
  Collapsable,
  ExpandIcon,
  CollapseIcon,
  IconButton,
} from './styled';

type Props = {
  label: string;
  panelPosition: 'RIGHT' | 'LEFT';
  isOverlay?: boolean;
  onToggle: () => void;
  isCollapsed: boolean;
  children?: React.ReactNode;
  maxWidth: number;
};

const CollapsablePanel: React.FC<Props> = ({
  label,
  panelPosition,
  maxWidth,
  isOverlay = false,
  children,
  isCollapsed,
  onToggle,
  ...props
}) => {
  const tooltipAlignment = panelPosition === 'RIGHT' ? 'left' : 'right';

  return (
    <Collapsable
      {...props}
      isCollapsed={isCollapsed}
      $panelPosition={panelPosition}
      $isOverlay={isOverlay}
      $maxWidth={maxWidth}
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
              align={tooltipAlignment}
              size="sm"
            >
              <CollapseIcon size={20} $panelPosition={panelPosition} />
            </IconButton>
          </Header>
          {children}
        </Panel>
      )}
    </Collapsable>
  );
};

export {CollapsablePanel};
