/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Button, LabeledInput, Modal, Form, Message} from 'components';

import {t} from 'translation';
import UserTypeahead from './UserTypeahead.js';

const defaultState = {
  selectedIdentity: null,
  activeRole: 'viewer'
};

export default class AddUserModal extends React.Component {
  state = defaultState;

  onConfirm = () => {
    const {selectedIdentity, activeRole} = this.state;
    if (!this.isValid()) {
      return;
    }

    this.props.onConfirm(selectedIdentity.id, selectedIdentity.type, activeRole);
    this.reset();
  };

  onClose = () => {
    this.props.onClose();
    this.reset();
  };

  reset = () => {
    this.setState(defaultState);
  };

  alreadyExists = () => {
    const {selectedIdentity} = this.state;
    if (!selectedIdentity) {
      return false;
    }

    return this.props.existingUsers.find(
      ({identity: {id, type}}) => type === selectedIdentity.type && id === selectedIdentity.id
    );
  };

  isValid = () => this.state.selectedIdentity && !this.alreadyExists();

  render() {
    const {open} = this.props;
    const {selectedIdentity, activeRole} = this.state;

    const alreadyExists = this.alreadyExists();

    const validInput = this.isValid();

    return (
      <Modal className="AddUserModal" open={open} onClose={this.onClose} onConfirm={this.onConfirm}>
        <Modal.Header>{t('home.roles.addUserTitle')}</Modal.Header>
        <Modal.Content>
          <Form>
            {t('home.userTitle')}
            <Form.Group>
              <UserTypeahead onChange={selectedIdentity => this.setState({selectedIdentity})} />
              {alreadyExists && selectedIdentity.type === 'user' && (
                <Message error>{t('home.roles.existing-user-error')}</Message>
              )}
              {alreadyExists && selectedIdentity.type === 'group' && (
                <Message error>{t('home.roles.existing-group-error')}</Message>
              )}
            </Form.Group>
            {t('home.roles.userRole')}
            <Form.Group>
              <LabeledInput
                checked={activeRole === 'viewer'}
                onChange={() => this.setState({activeRole: 'viewer'})}
                label={
                  <>
                    <h2>{t('home.roles.viewer')}</h2>
                    <p>{t('home.roles.viewer-description')}</p>
                  </>
                }
                type="radio"
              />
              <LabeledInput
                checked={activeRole === 'editor'}
                onChange={() => this.setState({activeRole: 'editor'})}
                label={
                  <>
                    <h2>{t('home.roles.editor')}</h2>
                    <p>{t('home.roles.editor-description')}</p>
                  </>
                }
                type="radio"
              />
              <LabeledInput
                checked={activeRole === 'manager'}
                onChange={() => this.setState({activeRole: 'manager'})}
                label={
                  <>
                    <h2>{t('home.roles.manager')}</h2>
                    <p>{t('home.roles.manager-description')}</p>
                  </>
                }
                type="radio"
              />
            </Form.Group>
          </Form>
        </Modal.Content>
        <Modal.Actions>
          <Button className="cancel" onClick={this.onClose}>
            {t('common.cancel')}
          </Button>
          <Button
            variant="primary"
            color="blue"
            className="confirm"
            disabled={!validInput}
            onClick={this.onConfirm}
          >
            {t('common.add')}
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }
}
