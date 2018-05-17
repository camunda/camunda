import React from 'react';

import {Modal, Button, ControlGroup, Input} from 'components';

import './ExternalModal.css';

export default class ExternalModal extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      source: ''
    };
  }

  addReport = props => {
    this.props.confirm({id: '', configuration: {external: this.state.source}});
  };

  render() {
    return (
      <Modal open onClose={this.props.close}>
        <Modal.Header>Add a Report</Modal.Header>
        <Modal.Content>
          <ControlGroup layout="centered">
            <label htmlFor="ExternalModal__input">
              Enter URL of external datasource to be included on the dashboard
            </label>
            <Input
              name="ExternalModal__input"
              className="ExternalModal__input"
              placeholder="https://www.example.com/widget/embed.html"
              value={this.state.source}
              onChange={({target: {value}}) =>
                this.setState({
                  source: value
                })
              }
            />
          </ControlGroup>
        </Modal.Content>
        <Modal.Actions>
          <Button onClick={this.props.close}>Cancel</Button>
          <Button
            type="primary"
            color="blue"
            onClick={this.addReport}
            disabled={!this.state.source}
          >
            Add Report
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }
}
