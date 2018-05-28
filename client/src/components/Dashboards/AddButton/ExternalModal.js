import React from 'react';

import {Modal, Button, ControlGroup, Input, ErrorMessage} from 'components';

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

  isValid = url => {
    // url has to start with https:// or http://
    return url.match(/^(https|http):\/\/.+/);
  };

  render() {
    const isInvalid = !this.isValid(this.state.source);

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
              isInvalid={isInvalid}
              onChange={({target: {value}}) =>
                this.setState({
                  source: value
                })
              }
            />
            {isInvalid && (
              <ErrorMessage className="ExternalModal__error">
                URL has to start with http:// or https://
              </ErrorMessage>
            )}
          </ControlGroup>
        </Modal.Content>
        <Modal.Actions>
          <Button onClick={this.props.close}>Cancel</Button>
          <Button type="primary" color="blue" onClick={this.addReport} disabled={isInvalid}>
            Add Report
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }
}
