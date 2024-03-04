/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {useEffect, useRef} from 'react';
import {autorun} from 'mobx';
import {observer} from 'mobx-react';
import {useNavigate} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {DrdViewer} from 'modules/dmn-js/DrdViewer';
import {decisionXmlStore} from 'modules/stores/decisionXml';
import {drdStore} from 'modules/stores/drd';
import {drdDataStore} from 'modules/stores/drdData';
import {PanelHeader, Container, Stack} from './styled';
import {tracking} from 'modules/tracking';
import {decisionDefinitionStore} from 'modules/stores/decisionDefinition';
import {Button} from '@carbon/react';
import {Close, Maximize, Minimize} from '@carbon/react/icons';
import {StateOverlay} from 'modules/components/StateOverlay';

const Drd: React.FC = observer(() => {
  const {
    setPanelState,
    state: {panelState},
  } = drdStore;
  const drdViewer = useRef<DrdViewer | null>(null);
  const drdViewerRef = useRef<HTMLDivElement | null>(null);
  const navigate = useNavigate();

  const handleDecisionSelection = (decisionId: string) => {
    const decisionInstances = drdDataStore.state.drdData?.[decisionId];

    if (decisionInstances === undefined) {
      return;
    }

    const decisionInstanceId =
      decisionInstances[decisionInstances.length - 1]?.decisionInstanceId;

    if (decisionInstanceId !== undefined) {
      navigate(Paths.decisionInstance(decisionInstanceId));
    }
  };

  if (drdViewer.current === null) {
    drdViewer.current = new DrdViewer(handleDecisionSelection);
  }

  useEffect(() => {
    const disposer = autorun(() => {
      if (
        drdViewerRef.current !== null &&
        decisionXmlStore.state.xml !== null
      ) {
        drdViewer.current!.render(
          drdViewerRef.current,
          decisionXmlStore.state.xml,
          drdDataStore.selectableDecisions,
          drdDataStore.currentDecision,
          drdDataStore.decisionStates,
        );
      }
    });
    return () => {
      disposer();
      drdViewer.current?.reset();
    };
  }, []);

  return (
    <Container data-testid="drd">
      <PanelHeader title={decisionDefinitionStore.name ?? ''}>
        <Stack orientation="horizontal">
          {panelState === 'minimized' && (
            <Button
              kind="ghost"
              hasIconOnly
              renderIcon={Maximize}
              tooltipPosition="left"
              iconDescription="Maximize DRD Panel"
              aria-label="Maximize DRD Panel"
              size="lg"
              onClick={() => {
                setPanelState('maximized');
                tracking.track({
                  eventName: 'drd-panel-interaction',
                  action: 'maximize',
                });
              }}
            />
          )}
          {panelState === 'maximized' && (
            <Button
              kind="ghost"
              hasIconOnly
              renderIcon={Minimize}
              tooltipPosition="left"
              iconDescription="Minimize DRD Panel"
              aria-label="Minimize DRD Panel"
              size="lg"
              onClick={() => {
                setPanelState('minimized');
                tracking.track({
                  eventName: 'drd-panel-interaction',
                  action: 'minimize',
                });
              }}
            />
          )}
          <Button
            kind="ghost"
            hasIconOnly
            renderIcon={Close}
            tooltipPosition="left"
            iconDescription="Close DRD Panel"
            aria-label="Close DRD Panel"
            size="lg"
            onClick={() => {
              setPanelState('closed');
              tracking.track({
                eventName: 'drd-panel-interaction',
                action: 'close',
              });
            }}
          />
        </Stack>
      </PanelHeader>
      <div data-testid="drd-viewer" ref={drdViewerRef} />

      {drdDataStore.state.decisionStateOverlays.map(
        ({decisionId, state, container}) => (
          <StateOverlay key={decisionId} state={state} container={container} />
        ),
      )}
    </Container>
  );
});

export {Drd};
