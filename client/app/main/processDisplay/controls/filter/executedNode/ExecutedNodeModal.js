import React from 'react';
import Modal from 'react-bootstrap/lib/Modal';
import {onNextTick, withState} from 'utils';
import {SelectNodeDiagram} from './SelectNodeDiagram';
import {addFlowNodesFilter} from './service';
import {createViewUtilsComponentFromReact} from 'reactAdapter';

const jsx = React.createElement;

export class ExecutedNodeModal extends React.PureComponent {
  constructor(props) {
    super(props);

    this.state = {
      diagramVisible: false,
      currentlySelected: []
    };
  }

  componentWillReceiveProps({isOpen}) {
    if (!isOpen) {
      this.setState({
        diagramVisible: false
      });
    }
  }

  render() {
    const {getDiagramXML} = this.props;
    const {diagramVisible} = this.state;
    const xml = getDiagramXML();

    return <Modal show={this.props.isOpen} onHide={this.close} onEntered={this.onEntered} dialogClassName="executed-node-modal">
      <Modal.Header>
        <button type="button" className="close" onClick={this.close}>
          <span>×</span>
        </button>
        <h4 className="modal-title">New Executed Node Filter</h4>
      </Modal.Header>
      <Modal.Body>
        <SelectNodeDiagram xml={xml} diagramVisible={diagramVisible} onSelectionChange={this.onSelectionChange} />
      </Modal.Body>
      <Modal.Footer>
        <button type="button" className="btn btn-default" onClick={this.close}>
          Abort
        </button>
        <button type="button" className="btn btn-primary" onClick={this.createFilter}>
          Create Filter
        </button>
      </Modal.Footer>
    </Modal>;
  }

  onEntered = () => {
    this.setState({
      diagramVisible: true
    });
  }

  onSelectionChange = selection => this.currentlySelected = selection;

  createFilter = () => {
    addFlowNodesFilter(this.currentlySelected);

    this.close();
    onNextTick(this.props.onFilterAdded);
  }

  close = () => {
    if (typeof this.props.setProperty === 'function') {
      this.props.setProperty('isOpen', false);
    }
  }
}

export function createExecutedNodeModal(onFilterAdded, getDiagramXML) {
  const ExecutedNodeModalWithState = withState(
    {
      isOpen: false
    },
    (props) => <ExecutedNodeModal {...props} onFilterAdded={onFilterAdded} getDiagramXML={getDiagramXML} />
  );
  const ExecutedNodeModalViewUtilsComponent = createViewUtilsComponentFromReact('div', ExecutedNodeModalWithState);

  ExecutedNodeModalViewUtilsComponent.open = () =>  ExecutedNodeModalWithState.setProperty('isOpen', true);
  ExecutedNodeModalViewUtilsComponent.close = () =>  ExecutedNodeModalWithState.setProperty('isOpen', false);

  return ExecutedNodeModalViewUtilsComponent;
}
