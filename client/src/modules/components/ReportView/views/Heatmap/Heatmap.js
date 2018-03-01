import React from 'react';

import {BPMNDiagram, TargetValueBadge} from 'components';
import HeatmapOverlay from './HeatmapOverlay';

import {calculateTargetValueHeat, convertToMilliseconds} from './service';
import {formatters} from 'services';

import './Heatmap.css';

const Heatmap = (props) => {
  const {xml} = props;
  const {data, errorMessage, targetValue} = props;

  if(!data || typeof data !== 'object') {
    return <p>{errorMessage}</p>;
  }

  if(!xml) {
    return <div className='heatmap-loading-indicator'>loading...</div>;
  }

  let heatmapComponent;
  if(targetValue && targetValue.active) {
    const heat = calculateTargetValueHeat(data, targetValue.values);
    heatmapComponent = [
      <HeatmapOverlay key='heatmap' data={heat} formatter={(_,id) => {
        const node = document.createElement('div');

        const target = convertToMilliseconds(targetValue.values[id].value, targetValue.values[id].unit);
        const real = data[id];

        node.innerHTML = `target duration: ${formatters.duration(target)}<br/>`;

        if(typeof real === 'number') {
          node.innerHTML += `actual duration: ${formatters.duration(real)}<br/>`;
        } else {
          node.innerHTML += `No actual value available.<br/>`;
        }

        if(heat[id]) {
          // above target value
          node.innerHTML += `${Math.round(heat[id] * 100)}% above target value`;
        } else {
          // no heat set: below target value
          if(typeof real === 'number') {
            node.innerHTML += `${Math.round((1 - real / target) * 100)}% below target value`;
          } else {
            node.innerHTML += `Cannot compare target and actual value.`;
          }
        }

        // tooltips don't work well with spaces
        node.innerHTML = node.innerHTML.replace(/ /g, '\u00A0');

        return node;
        }} />,
      <TargetValueBadge key='targetValueBadge' values={targetValue.values} />
    ];
  } else {
    heatmapComponent = <HeatmapOverlay data={data} formatter={props.formatter} />
  }

  return (<div className='Heatmap'>
    <BPMNDiagram xml={xml}>
      {heatmapComponent}
    </BPMNDiagram>
  </div>);
}

export default Heatmap;
