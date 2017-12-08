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

  isNodeSelected = () => {
    return this.state.selectedNodes.length>0;
  }

  createOperator  = (name) => {
    return <span className='NodeFilter__preview-item-operator'> {name} </span>;
  }

  createPreviewList = () => {
    const previewList = [];

    this.state.selectedNodes.forEach( (selectedNode, idx) => {
      previewList.push(
        <li key={idx} className='NodeFilter__preview-item'>
          <span key={idx}>
            {' '}<span className='NodeFilter__preview-item-value'>{selectedNode.toString()}</span>{' '}
            {idx < this.state.selectedNodes.length - 1 && this.createOperator('or')}
          </span>
        </li>
      )
    });
    return <ul className='NodeFilter__preview'>
              <span className='NodeFilter__preview-introduction'>This is the filter you are about to create: executed flow node</span>{' '}
              {previewList}
            </ul>;
  }

  render() {
    return (<Modal open={true} onClose={this.props.close} className='NodeFilter__modal'>
      <Modal.Header>New Flownode Filter</Modal.Header>
      <Modal.Content>
        {this.createPreviewList()}
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
        <Button type='primary' className='Button--blue' disabled={!this.isNodeSelected()} onClick={this.createFilter}>Create Filter</Button>
      </Modal.Actions>
    </Modal>);
  }
}
