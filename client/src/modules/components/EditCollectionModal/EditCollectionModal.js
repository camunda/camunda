/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Component} from 'react';
import {Button, LabeledInput, Modal, Form} from 'components';
import {t} from 'translation';

export default class EditCollectionModal extends Component {
  constructor(props) {
    super(props);
    this.state = {
      name: props.collection.name || t('common.collection.modal.defaultName'),
      loading: false
    };
  }

  onConfirm = () => {
    this.setState({loading: true});
    this.props.onConfirm(this.state.name);
  };

  getConfirmButtonText = () => {
    const {name} = this.props.collection;
    if (this.state.loading) {
      return name ? t('common.saving') : t('common.creating');
    }
    return name ? t('common.save') : t('common.collection.modal.createBtn');
  };

  render() {
    const {collection, onClose} = this.props;
    return (
      <Modal open onClose={onClose} onConfirm={this.onConfirm}>
        <Modal.Header>
          {collection.name
            ? t('common.collection.modal.title.edit')
            : t('common.collection.modal.title.new')}
        </Modal.Header>
        <Modal.Content>
          <Form>
            <Form.Group>
              <LabeledInput
                type="text"
                label={t('common.collection.modal.inputLabel')}
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
          <Button className="cancel" onClick={onClose} disabled={this.state.loading}>
            {t('common.cancel')}
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
