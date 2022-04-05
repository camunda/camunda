/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
}

const CollapsablePanel: React.FC<Props> = ({
  isInitiallyCollapsed,
  ...props
}) => {
  const {title, children, className} = props;
  const [isCollapsed, setIsCollapsed] = useState(isInitiallyCollapsed ?? false);

  return (
    <Container className={className}>
      {isCollapsed ? (
        <CollapsedPanel
          data-testid="collapsed-panel"
          onClick={() => {
            setIsCollapsed(!isCollapsed);
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
