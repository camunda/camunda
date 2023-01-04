/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState} from 'react';
import {Panel} from 'modules/components/Panel';
import {
  ExpandedPanel,
  CollapsedPanel,
  Title,
  Container,
  Expand,
  Collapse,
} from './styled';
import {IconButton} from '@carbon/react';

type Props = {
  children: React.ReactNode;
  title: string;
  className?: string;
  isInitiallyCollapsed?: boolean;
} & React.ComponentProps<typeof Panel>;

const CollapsablePanel: React.FC<Props> = ({
  isInitiallyCollapsed,
  ...props
}) => {
  const {title, children, className} = props;
  const [isCollapsed, setIsCollapsed] = useState(isInitiallyCollapsed ?? false);
  const [shouldAutoFocus, setShouldAutoFocus] = useState(
    isInitiallyCollapsed ?? false,
  );

  return (
    <Container className={className}>
      {isCollapsed ? (
        <CollapsedPanel
          data-testid="collapsed-panel"
          onClick={() => {
            setIsCollapsed(!isCollapsed);

            if (!shouldAutoFocus) {
              setShouldAutoFocus(true);
            }
          }}
        >
          <IconButton
            data-testid="collapse-button"
            onClick={() => {
              setIsCollapsed(!isCollapsed);
            }}
            autoFocus
            label="Expand task panel"
            kind="ghost"
            align="right"
            size="sm"
          >
            <Expand />
          </IconButton>

          <Title>{title}</Title>
        </CollapsedPanel>
      ) : (
        <ExpandedPanel data-testid="expanded-panel">
          <Panel
            {...props}
            Icon={
              <IconButton
                data-testid="collapse-button"
                onClick={() => {
                  setIsCollapsed(!isCollapsed);
                }}
                autoFocus={shouldAutoFocus}
                label="Close task panel"
                kind="ghost"
                align="bottom"
                size="sm"
              >
                <Collapse />
              </IconButton>
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
