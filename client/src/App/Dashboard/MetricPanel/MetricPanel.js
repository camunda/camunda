import React from 'react';

import {MetricTile} from '../MetricTile';

import * as Styled from './styled.js';

export default function MetricPanel({metricTiles}) {
  return (
    <Styled.Panel>
      <Styled.Ul>
        {metricTiles.map(({name, metric, metricColor}) => {
          return (
            <li key={name}>
              <MetricTile
                metric={metric}
                name={name}
                metricColor={metricColor}
              />
            </li>
          );
        })}
      </Styled.Ul>
    </Styled.Panel>
  );
}
