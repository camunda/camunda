/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Button, LabeledInput, Modal, Form, Typeahead, ErrorMessage} from 'components';
import {searchIdentities} from './service';

import {t} from 'translation';

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

  formatTypeaheadOption = ({name, email, id, type}) => {
    const subTexts = [];
    if (name) {
      subTexts.push(email);
    }

    if (name || email) {
      subTexts.push(id);
    }

    return {
      text: name || email || id,
      tag: type === 'group' && ' (User Group)',
      subTexts
    };
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
              <Typeahead
                placeholder={t('common.collection.addUserModal.searchPlaceholder')}
                values={searchIdentities}
                onSelect={identity => this.setState({selectedIdentity: identity})}
                formatter={this.formatTypeaheadOption}
              />
              {alreadyExists && selectedIdentity.type === 'user' && (
                <ErrorMessage>{t('home.roles.existing-user-error')}</ErrorMessage>
              )}
              {alreadyExists && selectedIdentity.type === 'group' && (
                <ErrorMessage>{t('home.roles.existing-group-error')}</ErrorMessage>
              )}
            </Form.Group>
            {t('home.roles.userRole')}
            <Form.Group>
              <LabeledInput
                checked={activeRole === 'viewer'}
                onChange={() => this.setState({activeRole: 'viewer'})}
                label={
                  <>
                    <h2 className="label">{t('home.roles.viewer')}</h2>
                    <p className="label">{t('home.roles.viewer-description')}</p>
                  </>
                }
                type="radio"
              />
              <LabeledInput
                checked={activeRole === 'editor'}
                onChange={() => this.setState({activeRole: 'editor'})}
                label={
                  <>
                    <h2 className="label">{t('home.roles.editor')}</h2>
                    <p className="label">{t('home.roles.editor-description')}</p>
                  </>
                }
                type="radio"
              />
              <LabeledInput
                checked={activeRole === 'manager'}
                onChange={() => this.setState({activeRole: 'manager'})}
                label={
                  <>
                    <h2 className="label">{t('home.roles.manager')}</h2>
                    <p className="label">{t('home.roles.manager-description')}</p>
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
