import React from 'react';
import {Modal, Button, BPMNDiagram} from 'components';

import {loadDiagramXML} from './service';
import ClickBehavior from './ClickBehavior';

import './NodeFilter.css';

export default class NodeFilter extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      xml: undefined,
      selectedNodes: []
    };

    this.loadDiagram();
  }

  loadDiagram = async () => {
    this.setState({
      xml: await loadDiagramXML(this.props.processDefinitionId)
    });
  }

  toggleNode = toggledNode => {
    if(this.state.selectedNodes.includes(toggledNode)) {
      this.setState({
        selectedNodes: this.state.selectedNodes.filter(node => node !== toggledNode)
      });
    } else {
      this.setState({
        selectedNodes: this.state.selectedNodes.concat([toggledNode])
      });
    }
  }

  createFilter = () => {
    this.props.addFilter({
      type: 'executedFlowNodes',
      data: {
        operator: 'in',
        values: this.state.selectedNodes
      }
    });
  }

  render() {
    return (<Modal open={true} onClose={this.props.close} className='NodeFilter__modal'>
      <Modal.Header>New Flownode Filter</Modal.Header>
      <Modal.Content>
        {this.state.xml && (
          <div className='NodeFilter__diagram-container'>
            <BPMNDiagram xml={this.state.xml}>
              <ClickBehavior onClick={this.toggleNode} selectedNodes={this.state.selectedNodes}/>
            </BPMNDiagram>
          </div>
        )}
      </Modal.Content>
      <Modal.Actions>
        <Button onClick={this.props.close}>Abort</Button>
        <Button type='primary' onClick={this.createFilter}>Create Filter</Button>
      </Modal.Actions>
    </Modal>);
  }
}
