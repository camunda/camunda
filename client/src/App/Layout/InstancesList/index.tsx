/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  ResizablePanel,
  SplitDirection,
} from 'modules/components/ResizablePanel';
import {Container} from './styled';
import {observer} from 'mobx-react';
import {useEffect, useRef, useState} from 'react';

type Props = {
  leftPanel?: React.ReactNode;
  topPanel: React.ReactNode;
  bottomPanel: React.ReactNode;
  additionalTopContent?: React.ReactNode;
  footer?: React.ReactNode;
  rightPanel?: React.ReactNode;
  type: 'process' | 'decision' | 'migrate';
};
const InstancesList: React.FC<Props> = observer(
  ({
    leftPanel,
    topPanel,
    bottomPanel,
    rightPanel,
    additionalTopContent,
    footer,
    type,
  }) => {
    const [clientHeight, setClientHeight] = useState(0);
    const containerRef = useRef<HTMLDivElement | null>(null);

    useEffect(() => {
      setClientHeight(containerRef?.current?.clientHeight ?? 0);
    }, []);

    const panelMinHeight = clientHeight / 4;

    return (
      <Container
        $hasLeftPanel={leftPanel !== undefined}
        $hasRightPanel={rightPanel !== undefined}
        $hasFooter={footer !== undefined}
        $hasAdditionalTopContent={additionalTopContent !== undefined}
      >
        {leftPanel}
        {additionalTopContent && <>{additionalTopContent}</>}
        <div ref={containerRef}>
          <ResizablePanel
            panelId={`${type}-instances-vertical-panel`}
            direction={SplitDirection.Vertical}
            minHeights={[panelMinHeight, panelMinHeight]}
          >
            {topPanel}
            {bottomPanel}
          </ResizablePanel>
        </div>
        {rightPanel}
        {footer}
      </Container>
    );
  },
);

export {InstancesList};
