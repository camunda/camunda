import React from 'react';

import {BPMNDiagram} from 'components';
import HeatmapOverlay from './HeatmapOverlay';

import './Heatmap.css';

const Heatmap = (props) => {
  const {xml} = props;
  const {data, errorMessage} = props;

  if(!data || typeof data !== 'object') {
    return <p>{errorMessage}</p>;
  }

  if(!xml) {
    return <div className='heatmap-loading-indicator'>loading...</div>;
  }

  return (<div className='Heatmap'>
    <BPMNDiagram xml={xml}>
      <HeatmapOverlay data={data} formatter={props.formatter} />
    </BPMNDiagram>
  </div>);
}

  export default Heatmap;
