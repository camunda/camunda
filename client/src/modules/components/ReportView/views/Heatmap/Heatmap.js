import React from 'react';

import {BPMNDiagram} from 'components';
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
    heatmapComponent = <HeatmapOverlay data={heat} formatter={(_,id) => {
      const node = document.createElement('div');

      const target = convertToMilliseconds(targetValue.values[id].value, targetValue.values[id].unit);
      const real = data[id];

      node.innerHTML =
        `target duration: ${formatters.duration(target)}<br/>` +
        `actual duration: ${formatters.duration(real)}<br/>`;

      if(heat[id]) {
        // above target value
        node.innerHTML += `${Math.round(heat[id] * 100)}% above target value`;
      } else {
        // no heat set: below target value
        node.innerHTML += `${Math.round((1 - real / target) * 100)}% below target value`;
      }

      // tooltips don't work well with spaces
      node.innerHTML = node.innerHTML.replace(/ /g, '\u00A0');

      return node;
    }} />;
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
