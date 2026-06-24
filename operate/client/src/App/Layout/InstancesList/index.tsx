/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  ResizablePanel,
  SplitDirection,
} from 'modules/components/ResizablePanel';
import {Container, PanelContainer} from './styled';
import {observer} from 'mobx-react';
import {useEffect, useRef, useState} from 'react';
import {Frame, type FrameProps} from 'modules/components/Frame';

type Props = {
  leftPanel?: React.ReactNode;
  topPanel: React.ReactNode;
  bottomPanel: React.ReactNode;
  additionalTopContent?: React.ReactNode;
  footer?: React.ReactNode;
  frame?: FrameProps;
  type: 'process' | 'decision' | 'migrate';
};
const InstancesList: React.FC<Props> = observer(
  ({
    leftPanel,
    topPanel,
    bottomPanel,
    additionalTopContent,
    footer,
    frame,
    type,
  }) => {
    const [clientHeight, setClientHeight] = useState(0);
    const containerRef = useRef<HTMLDivElement | null>(null);

    useEffect(() => {
      setClientHeight(containerRef?.current?.clientHeight ?? 0);
    }, []);

    const panelMinHeight = clientHeight / 4;

    return (
      <Frame frame={frame}>
        <Container
          $hasLeftPanel={leftPanel !== undefined}
          $hasFooter={footer !== undefined}
          $hasAdditionalTopContent={additionalTopContent !== undefined}
        >
          {leftPanel}
          {additionalTopContent && <>{additionalTopContent}</>}
          <PanelContainer ref={containerRef}>
            <ResizablePanel
              panelId={`${type}-instances-vertical-panel`}
              direction={SplitDirection.Vertical}
              minHeights={[panelMinHeight, panelMinHeight]}
            >
              {topPanel}
              {bottomPanel}
            </ResizablePanel>
          </PanelContainer>
          {footer}
        </Container>
      </Frame>
    );
  },
);

export {InstancesList};
