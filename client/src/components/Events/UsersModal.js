/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import update from 'immutability-helper';

import {Modal, Button, MultiUserTypeahead, Labeled} from 'components';
import {showError} from 'notifications';
import {withErrorHandling} from 'HOC';
import {t} from 'translation';

import {getUsers, getUser, updateUsers} from './service';

import './UsersModal.scss';

export class UsersModal extends React.Component {
  state = {
    loading: false,
    users: null,
    deleting: null,
  };

  componentDidMount() {
    this.props.mightFail(getUsers(this.props.id), (users) => this.setState({users}), showError);
  }

  addUser = (user) => {
    this.getSelectedUser(user, ({id, type, name, memberCount}) => {
      const newId = `${type.toUpperCase()}:${id}`;
      const newIdentity = {id: newId, identity: {id, name, type, memberCount}};
      this.setState(({users}) => ({
        users: update(users, {$push: [newIdentity]}),
      }));
    });
  };

  getSelectedUser = (user, cb) => {
    const {id, name} = user;
    if (this.state.users.some(({identity}) => identity.id === id)) {
      return showError(t('home.roles.existing-identity'));
    }

    if (!name) {
      return this.props.mightFail(getUser(id), cb, showError);
    }

    cb(user);
  };

  removeUser = (id) => {
    this.setState(({users}) => ({
      users: users.filter((user) => user.id !== id),
    }));
  };

  onConfirm = () => {
    this.setState({loading: true});
    this.props.mightFail(
      updateUsers(this.props.id, this.state.users),
      () => {
        this.setState({loading: false});
        this.props.onClose(this.state.users);
      },
      (error) => {
        showError(error);
        this.setState({loading: false});
      }
    );
  };

  close = () => this.props.onClose();

  render() {
    const {id} = this.props;
    const {loading, users} = this.state;
    const isValid = users && users.length > 0;

    return (
      <Modal open={id} onClose={this.close} onConfirm={this.onConfirm} className="UsersModal">
        <Modal.Header>{t('common.editAccess')}</Modal.Header>
        <Modal.Content>
          <p className="description">{t('events.permissions.description')}</p>
          <Labeled className="userTypeahead" label={t('home.userTitle')}>
            {users && (
              <MultiUserTypeahead
                users={users}
                onAdd={this.addUser}
                onRemove={this.removeUser}
                onClear={() => this.setState({users: []})}
              />
            )}
          </Labeled>
        </Modal.Content>
        <Modal.Actions>
          <Button main disabled={loading} onClick={this.close}>
            {t('common.cancel')}
          </Button>
          <Button main disabled={loading || !isValid} primary onClick={this.onConfirm}>
            {t('common.save')}
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }
}

export default withErrorHandling(UsersModal);
