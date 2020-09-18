/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';
import {Panel} from '../Panel';
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
}

const CollapsablePanel: React.FC<Props> = (props) => {
  const {title, children, className} = props;
  const [isExpanded, setIsExpanded] = useState(true);

  return (
    <Container isExpanded={isExpanded} className={className}>
      {isExpanded ? (
        <ExpandedPanel data-testid="expanded-panel">
          <Panel
            {...props}
            Icon={
              <CollapseButton
                data-testid="collapse-button"
                onClick={() => setIsExpanded(!isExpanded)}
              >
                <LeftIcon />
              </CollapseButton>
            }
          >
            {children}
          </Panel>
        </ExpandedPanel>
      ) : (
        <CollapsedPanel
          data-testid="collapsed-panel"
          onClick={() => setIsExpanded(!isExpanded)}
        >
          <Title>{title}</Title>
        </CollapsedPanel>
      )}
    </Container>
  );
};

export {CollapsablePanel};
