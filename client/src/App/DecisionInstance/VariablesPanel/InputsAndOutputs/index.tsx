/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observer} from 'mobx-react';
import {StatusMessage} from 'modules/components/StatusMessage';
import {decisionInstanceStore} from 'modules/stores/decisionInstance';
import {Panel, Title, SkeletonBlock} from './styled';
import {Table, TR, TH, TD} from 'modules/components/VariablesTable';
import {Skeleton} from './Skeleton';

const InputsAndOutputs: React.FC = observer(() => {
  const {
    state: {status, decisionInstance},
  } = decisionInstanceStore;
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

  return (
    <>
      <Panel>
        {status !== 'error' && <Title>Inputs</Title>}
        {status === 'initial' && (
          <Skeleton
            structure={INPUTS_STRUCTURE}
            data-testid="inputs-skeleton"
          />
        )}
        {status === 'fetched' && (
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
              {decisionInstance?.inputs.map(({id, name, value}) => {
                return (
                  <TR key={id}>
                    <TD>{name}</TD>
                    <TD>{value}</TD>
                  </TR>
                );
              })}
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
        {status === 'fetched' && (
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
              {decisionInstance?.outputs.map(({id, rule, name, value}) => {
                return (
                  <TR key={id}>
                    <TD>{rule}</TD>
                    <TD>{name}</TD>
                    <TD>{value}</TD>
                  </TR>
                );
              })}
            </tbody>
          </Table>
        )}
        {status === 'error' && (
          <StatusMessage variant="error">
            Data could not be fetched
          </StatusMessage>
        )}
      </Panel>
    </>
  );
});

export {InputsAndOutputs};
