/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useRef, useState} from 'react';
import {observer} from 'mobx-react';
import {StatusMessage} from 'modules/components/StatusMessage';
import {decisionInstanceDetailsStore} from 'modules/stores/decisionInstanceDetails';
import {Panel, Title, SkeletonBlock, PanelContainer} from './styled';
import {Table, TR, TH, TD} from 'modules/components/VariablesTable';
import {Skeleton} from './Skeleton';
import {
  ResizablePanel,
  SplitDirection,
} from 'modules/components/ResizablePanel';

const InputsAndOutputs: React.FC = observer(() => {
  const {
    state: {status, decisionInstance},
  } = decisionInstanceDetailsStore;
  const INPUTS_STRUCTURE = [
    {
      header: 'Name',
      component: <SkeletonBlock $width="200px" />,
      columnWidth: 'calc(100% / 3)',
    },
    {
      header: 'Value',
      component: <SkeletonBlock $width="400px" />,
      columnWidth: 'calc((100% / 3) * 2)',
    },
  ] as const;
  const OUTPUTS_STRUCTURE = [
    {
      header: 'Rule',
      component: <SkeletonBlock $width="40px" />,
      columnWidth: 'calc(100% / 6)',
    },
    {
      header: 'Name',
      component: <SkeletonBlock $width="200px" />,
      columnWidth: 'calc((100% / 6) * 2)',
    },
    {
      header: 'Value',
      component: <SkeletonBlock $width="300px" />,
      columnWidth: '50%',
    },
  ] as const;

  const containerRef = useRef<HTMLDivElement | null>(null);
  const [clientWidth, setClientWidth] = useState(0);

  useEffect(() => {
    setClientWidth(containerRef?.current?.clientWidth ?? 0);
  }, []);

  const panelMinWidth = clientWidth / 3;

  return (
    <PanelContainer ref={containerRef}>
      <ResizablePanel
        panelId="decision-instance-horizontal-panel"
        direction={SplitDirection.Horizontal}
        minWidths={[panelMinWidth, panelMinWidth]}
      >
        <Panel $hasBorder>
          {status !== 'error' && <Title>Inputs</Title>}
          {status === 'initial' && (
            <Skeleton
              structure={INPUTS_STRUCTURE}
              data-testid="inputs-skeleton"
            />
          )}
          {status === 'fetched' &&
            decisionInstance?.state === 'FAILED' &&
            decisionInstance?.evaluatedInputs.length === 0 && (
              <StatusMessage variant="default">
                No input available because the evaluation failed
              </StatusMessage>
            )}
          {status === 'fetched' &&
            decisionInstance?.evaluatedInputs !== undefined &&
            decisionInstance.evaluatedInputs.length > 0 && (
              <Table>
                <thead>
                  <TR>
                    {INPUTS_STRUCTURE.map(({header, columnWidth}, index) => (
                      <TH key={index} $width={columnWidth}>
                        {header}
                      </TH>
                    ))}
                  </TR>
                </thead>
                <tbody>
                  {decisionInstance?.evaluatedInputs.map(
                    ({id, name, value}) => {
                      return (
                        <TR key={id}>
                          <TD>{name}</TD>
                          <TD>{value}</TD>
                        </TR>
                      );
                    }
                  )}
                </tbody>
              </Table>
            )}
          {status === 'error' && (
            <StatusMessage variant="error">
              Data could not be fetched
            </StatusMessage>
          )}
        </Panel>
        <Panel>
          {status !== 'error' && <Title>Outputs</Title>}
          {status === 'initial' && (
            <Skeleton
              structure={OUTPUTS_STRUCTURE}
              data-testid="outputs-skeleton"
            />
          )}
          {status === 'fetched' && decisionInstance?.state !== 'FAILED' && (
            <Table>
              <thead>
                <TR>
                  {OUTPUTS_STRUCTURE.map(({header, columnWidth}, index) => (
                    <TH key={index} $width={columnWidth}>
                      {header}
                    </TH>
                  ))}
                </TR>
              </thead>
              <tbody>
                {decisionInstance?.evaluatedOutputs.map(
                  ({ruleIndex, name, value}) => {
                    return (
                      <TR key={ruleIndex}>
                        <TD>{ruleIndex}</TD>
                        <TD>{name}</TD>
                        <TD>{value}</TD>
                      </TR>
                    );
                  }
                )}
              </tbody>
            </Table>
          )}
          {status === 'error' && (
            <StatusMessage variant="error">
              Data could not be fetched
            </StatusMessage>
          )}
          {decisionInstance?.state === 'FAILED' && (
            <StatusMessage variant="default">
              No output available because the evaluation failed
            </StatusMessage>
          )}
        </Panel>
      </ResizablePanel>
    </PanelContainer>
  );
});

export {InputsAndOutputs};
