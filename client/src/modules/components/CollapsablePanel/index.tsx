/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';
import {Panel} from 'modules/components/Panel';
import {
  ExpandedPanel,
  CollapsedPanel,
  Title,
  CollapseButton,
  LeftIcon,
  Container,
} from './styled';

interface Props extends React.ComponentProps<typeof Panel> {
  children: React.ReactNode;
  title: string;
  className?: string;
  isInitiallyCollapsed?: boolean;
  onCollapse?: () => void;
  onExpand?: () => void;
}

const CollapsablePanel: React.FC<Props> = ({
  isInitiallyCollapsed,
  ...props
}) => {
  const {title, children, className, onCollapse, onExpand} = props;
  const [isCollapsed, setIsCollapsed] = useState(isInitiallyCollapsed ?? false);

  return (
    <Container className={className}>
      {isCollapsed ? (
        <CollapsedPanel
          data-testid="collapsed-panel"
          onClick={() => {
            setIsCollapsed(!isCollapsed);
            onExpand?.();
          }}
        >
          <Title>{title}</Title>
        </CollapsedPanel>
      ) : (
        <ExpandedPanel data-testid="expanded-panel">
          <Panel
            {...props}
            Icon={
              <CollapseButton
                data-testid="collapse-button"
                onClick={() => {
                  setIsCollapsed(!isCollapsed);
                  onCollapse?.();
                }}
              >
                <LeftIcon />
              </CollapseButton>
            }
          >
            {children}
          </Panel>
        </ExpandedPanel>
      )}
    </Container>
  );
};

export {CollapsablePanel};
