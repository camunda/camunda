import React from 'react';

import {loadProcessDefinitionXml} from './service';

import Diagram from './Diagram';
import HeatmapOverlay from './HeatmapOverlay';

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
    const {data} = this.props;

    if(!data || typeof data !== 'object') {
      return <p>Cannot display data. Choose another visualization.</p>;
    }

    if(!xml) {
      return <div>loading...</div>;
    }

    return <Diagram xml={xml}>
      <HeatmapOverlay data={data} />
    </Diagram>;
  }
}
