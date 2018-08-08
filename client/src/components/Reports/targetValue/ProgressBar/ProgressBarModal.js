import React from 'react';

import {Modal, Button} from 'components';

import NumberInputs from './NumberInputs';
import DurationInputs from './DurationInputs';

export default class ProgressBarModal extends React.Component {
  state = {
    data: {},
    isValid: true
  };

  getInputsComponent = () => (this.props.type === 'number' ? NumberInputs : DurationInputs);

  render() {
    const InputsComponent = this.getInputsComponent();

    return (
      <Modal
        open={this.props.open}
        onClose={this.props.onClose}
        onConfirm={this.state.isValid ? this.confirmModal : undefined}
        className="ProgressBarModal__modal"
      >
        <Modal.Header>Set Target Value</Modal.Header>
        <Modal.Content>
          <div className="ProgressBarModal__inputs">
            <InputsComponent
              {...this.props}
              data={this.state.data}
              setData={data => this.setState({data})}
              setValid={isValid => this.setState({isValid})}
            />
          </div>
        </Modal.Content>
        <Modal.Actions>
          <Button onClick={this.props.onClose}>Cancel</Button>
          <Button
            type="primary"
            color="blue"
            onClick={this.confirmModal}
            disabled={!this.state.isValid}
          >
            Apply
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }

  confirmModal = () => {
    this.props.onConfirm(this.getInputsComponent().sanitizeData(this.state.data));
  };
}
