import React from 'react';

import {StatsTile} from '../StatsTile';

import * as Styled from './styled.js';

const valueTiles = [
  {value: 62905, name: 'Instances running', valueColor: ''},
  {value: 436432, name: 'Active', valueColor: '#10d070'},
  {value: 193473, name: 'Incidents', valueColor: '#ff3d3d'}
];

export default function StatsPanel() {
  return (
    <Styled.Panel>
      <Styled.Ul>
        {valueTiles.map(({name, value, valueColor}, index) => {
          return (
            <li key={name}>
              <StatsTile
                value={value}
                name={name}
                valueColor={valueColor}
                themed={index === 0}
              />
            </li>
          );
        })}
      </Styled.Ul>
    </Styled.Panel>
  );
}
