import React from 'react';

import {StatsTile} from '../StatsTile';

import * as Styled from './styled.js';

const valueTiles = [
  {value: 62905, name: 'Instances running', valueColor: 'themed'},
  {value: 436432, name: 'Active', valueColor: 'allIsWell'},
  {value: 193473, name: 'Incidents', valueColor: 'incidentsAndErrors'}
];

export default function StatsPanel() {
  return (
    <Styled.Panel>
      <Styled.Ul>
        {valueTiles.map(({name, value, valueColor}) => {
          return (
            <li key={name}>
              <StatsTile value={value} name={name} valueColor={valueColor} />
            </li>
          );
        })}
      </Styled.Ul>
    </Styled.Panel>
  );
}
