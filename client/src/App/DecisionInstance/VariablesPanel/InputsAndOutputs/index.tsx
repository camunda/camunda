/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Panel, Title} from './styled';

const InputsAndOutputs: React.FC = () => {
  return (
    <>
      <Panel>
        <Title>Inputs</Title>
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Value</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>Age</td>
              <td>16</td>
            </tr>
            <tr>
              <td>Stateless Person</td>
              <td>false</td>
            </tr>
            <tr>
              <td>Parent is Norwegian</td>
              <td>"missing data"</td>
            </tr>
            <tr>
              <td>Previously Norweigian</td>
              <td>true</td>
            </tr>
          </tbody>
        </table>
      </Panel>
      <Panel>
        <Title>Outputs</Title>
        <table>
          <thead>
            <tr>
              <th>Rule</th>
              <th>Name</th>
              <th>Value</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>5</td>
              <td>Age requirements satisfied</td>
              <td>"missing data"</td>
            </tr>
            <tr>
              <td>5</td>
              <td>paragraph</td>
              <td>"sbl §17"</td>
            </tr>
          </tbody>
        </table>
      </Panel>
    </>
  );
};

export {InputsAndOutputs};
