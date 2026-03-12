/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {IS_NEW_PROCESS_INSTANCE_PAGE} from 'modules/feature-flags';
import {BottomPanelGrid, ResizableBottomPanelContainer} from './styled';
import {ElementInstanceLog} from './ElementInstanceLog';
import {VariablePanel} from './BottomPanel/VariablePanel';
import {useEffect, useRef, useState} from 'react';
import {
  ResizablePanel,
  SplitDirection,
} from 'modules/components/ResizablePanel';
import {BottomPanelTabs} from './BottomPanelTabs';

type Props = {
  isListenerTabSelected: boolean;
  setListenerTabVisibility: React.Dispatch<React.SetStateAction<boolean>>;
};

const BottomPanel: React.FC<Props> = (props) => {
  if (!IS_NEW_PROCESS_INSTANCE_PAGE) {
    return (
      <BottomPanelGrid $shouldExpandPanel={props.isListenerTabSelected}>
        <ElementInstanceLog />
        <VariablePanel
          setListenerTabVisibility={props.setListenerTabVisibility}
        />
      </BottomPanelGrid>
    );
  }

  // TODO: Static conditional. Reenable rule of hooks once the feature is removed.
  /* eslint-disable react-hooks/rules-of-hooks */
  const [clientWidth, setClientWidth] = useState(0);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const panelMinWidth = clientWidth / 4;

  useEffect(() => setClientWidth(containerRef?.current?.clientWidth ?? 0), []);
  /* eslint-enable react-hooks/rules-of-hooks */

  return (
    <ResizableBottomPanelContainer ref={containerRef}>
      <ResizablePanel
        panelId="process-instance-bottom-horizontal-panel"
        direction={SplitDirection.Horizontal}
        minWidths={[panelMinWidth, panelMinWidth]}
      >
        <ElementInstanceLog />
        <BottomPanelTabs />
      </ResizablePanel>
    </ResizableBottomPanelContainer>
  );
};

export {BottomPanel};
