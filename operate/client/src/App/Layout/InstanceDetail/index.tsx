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
import {Loading} from '@carbon/react';

type Props = {
  header: React.ReactNode;
  frameHeader?: React.ReactNode;
  footer?: React.ReactNode;
  breadcrumb?: React.ReactNode;
  topPanel: React.ReactNode;
  bottomPanel: React.ReactNode;
  rightPanel?: React.ReactNode;
  type: 'process' | 'decision';
  hasLoadingOverlay?: boolean;
  hasRightOverlay?: boolean;
};
const InstanceDetail: React.FC<Props> = observer(
  ({
    type,
    header,
    breadcrumb,
    footer,
    topPanel,
    bottomPanel,
    rightPanel,
    hasLoadingOverlay,
    hasRightOverlay,
  }) => {
    const [clientHeight, setClientHeight] = useState(0);
    const containerRef = useRef<HTMLDivElement | null>(null);

    useEffect(() => {
      setClientHeight(containerRef?.current?.clientHeight ?? 0);
    }, []);

    const panelMinHeight = clientHeight / 4;

    return (
      <Container
        $hasBreadcrumb={breadcrumb !== undefined}
        $hasFooter={footer !== undefined}
        $hasRightOverlay={hasRightOverlay}
        $hasRightPanel={rightPanel !== undefined}
      >
        {hasLoadingOverlay && <Loading data-testid="loading-overlay" />}
        {breadcrumb}
        {header}
        <PanelContainer ref={containerRef} $hasRightOverlay={hasRightOverlay}>
          <ResizablePanel
            panelId={`${type}-detail-vertical-panel`}
            direction={SplitDirection.Vertical}
            minHeights={[panelMinHeight, panelMinHeight]}
          >
            {topPanel}
            {bottomPanel}
          </ResizablePanel>
        </PanelContainer>
        {rightPanel}
        {footer}
      </Container>
    );
  },
);

export {InstanceDetail};
