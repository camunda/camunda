/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Component} from 'react';

import {Button, LabeledInput, Modal, Form, Input} from 'components';
import {t} from 'translation';

const defaultState = {
  userName: '',
  groupName: '',
  activeInput: 'user',
  activeRole: 'viewer'
};

export default class AddUserModal extends Component {
  state = defaultState;

  onConfirm = () => {
    const {userName, groupName, activeInput, activeRole} = this.state;

    this.props.onConfirm(activeInput === 'user' ? userName : groupName, activeInput, activeRole);
    this.reset();
  };

  onClose = () => {
    this.props.onClose();
    this.reset();
  };

  reset = () => {
    this.setState(defaultState);
  };

  render() {
    const {open} = this.props;
    const {userName, groupName, activeInput, activeRole} = this.state;

    const validInput = !!(activeInput === 'user' ? userName : groupName);

    return (
      <Modal className="AddUserModal" open={open} onClose={this.onClose} onConfirm={this.onConfirm}>
        <Modal.Header>{t('home.roles.addUserTitle')}</Modal.Header>
        <Modal.Content>
          <Form>
            {t('common.selectWithoutEllipsis')}
            <Form.Group>
              <Form.Group noSpacing>
                <Input
                  type="radio"
                  onChange={() => this.setState({activeInput: 'user'})}
                  checked={activeInput === 'user'}
                />
                <LabeledInput
                  type="text"
                  label={t('common.user.id')}
                  className="userIdInput"
                  value={userName}
                  onClick={() => this.setState({activeInput: 'user'})}
                  onChange={({target: {value}}) => this.setState({userName: value})}
                  autoComplete="off"
                />
              </Form.Group>
              <Form.Group noSpacing>
                <Input
                  type="radio"
                  className="groupIdRadio"
                  onChange={() => this.setState({activeInput: 'group'})}
                  checked={activeInput === 'group'}
                />
                <LabeledInput
                  type="text"
                  label={t('common.user-group.id')}
                  className="groupIdInput"
                  value={groupName}
                  onClick={() => this.setState({activeInput: 'group'})}
                  onChange={({target: {value}}) => this.setState({groupName: value})}
                  autoComplete="off"
                />
              </Form.Group>
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
