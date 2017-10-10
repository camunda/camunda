import React from 'react';
const jsx = React.createElement;

import {createDiagram} from 'widgets';
import {createOverlaysRenderer} from './overlaysRenderer';
import {TargetValueModal} from './TargetValueModal';

const Diagram = createDiagram();

export class TargetValueDisplay extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      modalOpen: false,
      selectedElement: null
    };
  }

  closeModal = () => {
    this.setState({
      modalOpen: false
    });
  }

  openModalFor = element => {
    this.setState({
      modalOpen: true,
      selectedElement: element
    });
  }

  render() {
    const {selectedElement, modalOpen} = this.state;

    return (<div>
      <Diagram {...this.props} createOverlaysRenderer={createOverlaysRenderer(this.openModalFor)} />
      <TargetValueModal targetValues={this.props.targetValue.data} isOpen={modalOpen} element={selectedElement} close={this.closeModal} />
    </div>);
  }
}
