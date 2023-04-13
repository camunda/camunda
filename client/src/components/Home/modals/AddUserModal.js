/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Button} from '@carbon/react';

import {LabeledInput, CarbonModal as Modal, Form, UserTypeahead} from 'components';
import {t} from 'translation';

const defaultState = {
  users: [],
  activeRole: 'viewer',
};

export default class AddUserModal extends React.Component {
  state = defaultState;

  onConfirm = () => {
    const {users, activeRole} = this.state;
    if (!users.length) {
      return;
    }

    this.props.onConfirm(users.map(({identity}) => ({role: activeRole, identity})));
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
    const {optimizeProfile, open, existingUsers} = this.props;
    const {users, activeRole} = this.state;

    return (
      <Modal className="AddUserModal" open={open} onClose={this.onClose} isOverflowVisible>
        <Modal.Header>
          {optimizeProfile === 'platform'
            ? t('home.roles.addUserGroupTitle')
            : t('home.roles.addUserTitle')}
        </Modal.Header>
        <Modal.Content>
          <Form>
            {optimizeProfile === 'platform' ? t('home.userGroupsTitle') : t('home.userTitle')}
            <Form.Group>
              <UserTypeahead
                users={users}
                collectionUsers={existingUsers}
                onChange={(users) => this.setState({users})}
                optionsOnly={optimizeProfile === 'cloud'}
              />
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
        <Modal.Footer>
          <Button kind="secondary" className="cancel" onClick={this.onClose}>
            {t('common.cancel')}
          </Button>
          <Button className="confirm" disabled={!users.length} onClick={this.onConfirm}>
            {t('common.add')}
          </Button>
        </Modal.Footer>
      </Modal>
    );
  }
}
