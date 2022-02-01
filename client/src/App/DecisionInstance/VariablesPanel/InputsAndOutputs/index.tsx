/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observer} from 'mobx-react';
import {StatusMessage} from 'modules/components/StatusMessage';
import {decisionInstanceStore} from 'modules/stores/decisionInstance';
import {Panel, Title} from './styled';

const InputsAndOutputs: React.FC = observer(() => {
  const {
    state: {status, decisionInstance},
  } = decisionInstanceStore;

  return (
    <>
      <Panel>
        <Title>Inputs</Title>
        {status === 'initial' && (
          <div data-testid="inputs-loading">loading</div>
        )}
        {status === 'fetched' && (
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Value</th>
              </tr>
            </thead>
            <tbody>
              {decisionInstance?.inputs.map(({id, name, value}) => {
                return (
                  <tr key={id}>
                    <td>{name}</td>
                    <td>{value}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
        {status === 'error' && (
          <StatusMessage variant="error">Cannot load inputs</StatusMessage>
        )}
      </Panel>
      <Panel>
        <Title>Outputs</Title>
        {status === 'initial' && (
          <div data-testid="outputs-loading">loading</div>
        )}
        {status === 'fetched' && (
          <table>
            <thead>
              <tr>
                <th>Rule</th>
                <th>Name</th>
                <th>Value</th>
              </tr>
            </thead>
            <tbody>
              {decisionInstance?.outputs.map(({id, rule, name, value}) => {
                return (
                  <tr key={id}>
                    <td>{rule}</td>
                    <td>{name}</td>
                    <td>{value}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
        {status === 'error' && (
          <StatusMessage variant="error">Cannot load outputs</StatusMessage>
        )}
      </Panel>
    </>
  );
});

export {InputsAndOutputs};
