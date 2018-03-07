import React from 'react';
import {Modal, Button, BPMNDiagram} from 'components';

import ClickBehavior from './ClickBehavior';

import './NodeFilter.css';

export default class NodeFilter extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      selectedNodes: this.props.filterData ? this.props.filterData[0].data.values : []
    };
  }

  toggleNode = toggledNode => {
    if (this.state.selectedNodes.includes(toggledNode)) {
      this.setState({
        selectedNodes: this.state.selectedNodes.filter(node => node !== toggledNode)
      });
    } else {
      this.setState({
        selectedNodes: this.state.selectedNodes.concat([toggledNode])
      });
    }
  };

  createFilter = () => {
    const values = this.state.selectedNodes.map(node => node.id);

    this.props.addFilter({
      type: 'executedFlowNodes',
      data: {
        operator: 'in',
        values
      }
    });
  };

  isNodeSelected = () => {
    return this.state.selectedNodes.length > 0;
  };

  createOperator = name => {
    return <span className="NodeFilter__preview-item-operator"> {name} </span>;
  };

  createPreviewList = () => {
    const previewList = [];

    this.state.selectedNodes.forEach((selectedNode, idx) => {
      previewList.push(
        <li key={idx} className="NodeFilter__preview-item">
          <span key={idx}>
            {' '}
            <span className="NodeFilter__preview-item-value">{selectedNode.name}</span>{' '}
            {idx < this.state.selectedNodes.length - 1 && this.createOperator('or')}
          </span>
        </li>
      );
    });
    return (
      <ul className="NodeFilter__preview">
        <span className="NodeFilter__preview-introduction">
          This is the filter you are about to create:{' '}
        </span>{' '}
        <span className="NodeFilter__parameter-name">Executed Flow Node</span> is {previewList}
      </ul>
    );
  };

  setSelectedNodes = nodes => {
    this.setState({
      selectedNodes: nodes
    });
  };

  render() {
    return (
      <Modal open={true} onClose={this.props.close} className="NodeFilter__modal">
        <Modal.Header>Add Flow Node Filter</Modal.Header>
        <Modal.Content>
          {this.createPreviewList()}
          {this.props.xml && (
            <div className="NodeFilter__diagram-container">
              <BPMNDiagram xml={this.props.xml}>
                <ClickBehavior
                  setSelectedNodes={this.setSelectedNodes}
                  onClick={this.toggleNode}
                  selectedNodes={this.state.selectedNodes}
                />
              </BPMNDiagram>
            </div>
          )}
        </Modal.Content>
        <Modal.Actions>
          <Button onClick={this.props.close}>Cancel</Button>
          <Button
            type="primary"
            color="blue"
            disabled={!this.isNodeSelected()}
            onClick={this.createFilter}
          >
            {this.props.filterData ? 'Edit ' : 'Add '}Filter
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }
}
