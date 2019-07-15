/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Component} from 'react';
import {Button, LabeledInput, Modal, Form} from 'components';

export default class EditCollectionModal extends Component {
  constructor(props) {
    super(props);
    this.state = {
      name: props.collection.name || 'New Collection',
      loading: false
    };
  }

  inputRef = input => {
    this.nameInput = input;
  };

  componentDidMount() {
    if (this.nameInput) {
      this.nameInput.focus();
      this.nameInput.select();
    }
  }

  onConfirm = () => {
    this.setState({loading: true});
    this.props.onConfirm(this.state.name);
  };

  getConfirmButtonText = () => {
    const {name} = this.props.collection;
    if (this.state.loading) {
      return name ? 'Saving...' : 'Creating...';
    }
    return name ? 'Save' : 'Create Collection';
  };

  render() {
    const {collection, onClose} = this.props;
    return (
      <Modal open onClose={onClose} onConfirm={this.onConfirm}>
        <Modal.Header>
          {collection.name ? 'Edit Collection name' : 'Create new Collection'}
        </Modal.Header>
        <Modal.Content>
          <Form>
            <Form.Group>
              <LabeledInput
                type="text"
                label="Add Name"
                ref={this.inputRef}
                style={{width: '100%'}}
                value={this.state.name}
                onChange={({target: {value}}) => this.setState({name: value})}
                disabled={this.state.loading}
                autoComplete="off"
              />
            </Form.Group>
          </Form>
        </Modal.Content>
        <Modal.Actions>
          <Button
            className="cancel"
            variant="primary"
            onClick={onClose}
            disabled={this.state.loading}
          >
            Cancel
          </Button>
          <Button
            variant="primary"
            color="blue"
            className="confirm"
            disabled={!this.state.name || this.state.loading}
            onClick={this.onConfirm}
          >
            {this.getConfirmButtonText()}
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }
}

EditCollectionModal.defaultProps = {
  collection: {}
};
