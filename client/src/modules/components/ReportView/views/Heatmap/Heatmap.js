import React from 'react';

import {loadProcessDefinitionXml} from './service';

import {BPMNDiagram} from 'components';
import HeatmapOverlay from './HeatmapOverlay';

import './Heatmap.css';

export default class Heatmap extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      xml: null
    };

    this.load();
  }

  async load() {
    const xml = await loadProcessDefinitionXml(this.props.process);

    this.setState({xml});
  }

  render() {
    const {xml} = this.state;
    const {data, errorMessage} = this.props;

    if(!data || typeof data !== 'object') {
      return <p>{errorMessage}</p>;
    }

    if(!xml) {
      return <div className='heatmap-loading-indicator'>loading...</div>;
    }

    return (<div className='Heatmap'>
      <BPMNDiagram style={{height: '100%', width:'100%'}} xml={xml}>
        <HeatmapOverlay data={data} />
      </BPMNDiagram>
    </div>);
  }
}
