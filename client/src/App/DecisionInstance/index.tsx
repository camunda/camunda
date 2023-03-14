/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useRef, useState} from 'react';
import {useParams} from 'react-router-dom';
import {observer} from 'mobx-react';
import {decisionInstanceDetailsStore} from 'modules/stores/decisionInstanceDetails';
import {drdDataStore} from 'modules/stores/drdData';
import {drdStore} from 'modules/stores/drd';
import {DecisionPanel} from './DecisionPanel';
import {Header} from './Header';
import {VariablesPanel} from './VariablesPanel';
import {DrdPanel} from './DrdPanel';
import {Container, DecisionInstanceContainer, PanelContainer} from './styled';
import {Drd} from './Drd';
import {decisionXmlStore} from 'modules/stores/decisionXml';
import {
  ResizablePanel,
  SplitDirection,
} from 'modules/components/ResizablePanel';
import {PAGE_TITLE} from 'modules/constants';
import {tracking} from 'modules/tracking';
import {Forbidden} from 'modules/components/Forbidden';

const DecisionInstance: React.FC = observer(() => {
  const {decisionInstanceId = ''} = useParams<{decisionInstanceId: string}>();
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [clientHeight, setClientHeight] = useState(0);
  const {decisionInstance} = decisionInstanceDetailsStore.state;
  const decisionName = decisionInstance?.decisionName;

  useEffect(() => {
    setClientHeight(containerRef?.current?.clientHeight ?? 0);
  }, []);

  useEffect(() => {
    drdDataStore.init();
    decisionXmlStore.init();

    return () => {
      decisionInstanceDetailsStore.reset();
      drdDataStore.reset();
      decisionXmlStore.reset();
    };
  }, []);

  useEffect(() => {
    decisionInstanceDetailsStore.fetchDecisionInstance(decisionInstanceId);
  }, [decisionInstanceId]);

  useEffect(() => {
    if (decisionInstanceId !== '' && decisionName !== undefined)
      document.title = PAGE_TITLE.DECISION_INSTANCE(
        decisionInstanceId,
        decisionName
      );
  }, [decisionInstanceId, decisionName]);

  useEffect(() => {
    if (decisionInstance !== null) {
      tracking.track({
        eventName: 'decision-instance-details-loaded',
        state: decisionInstance.state,
      });
    }
  }, [decisionInstance]);

  const panelMinHeight = clientHeight / 4;

  return (
    <Container>
      {(() => {
        if (decisionInstanceDetailsStore.state.status === 'forbidden') {
          return <Forbidden />;
        }
        if (drdStore.state.panelState === 'maximized') {
          return <Drd />;
        }

        return (
          <DecisionInstanceContainer>
            <Header />
            <PanelContainer ref={containerRef}>
              <ResizablePanel
                panelId="decision-instance-vertical-panel"
                direction={SplitDirection.Vertical}
                minHeights={[panelMinHeight, panelMinHeight]}
              >
                <DecisionPanel />
                <VariablesPanel />
              </ResizablePanel>
            </PanelContainer>

            {drdStore.state.panelState === 'minimized' && (
              <DrdPanel>
                <Drd />
              </DrdPanel>
            )}
          </DecisionInstanceContainer>
        );
      })()}
    </Container>
  );
});

export {DecisionInstance};
