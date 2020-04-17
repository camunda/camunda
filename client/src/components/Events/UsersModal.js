/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Modal, Button, UserTypeahead, EntityList, Message, Labeled} from 'components';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {getUsers, getUser, updateUsers} from './service';
import {showError} from 'notifications';
import update from 'immutability-helper';
import './UsersModal.scss';

export default withErrorHandling(
  class UsersModal extends React.Component {
    state = {
      loading: false,
      users: null,
      deleting: null,
      alreadyExists: false,
      selectedIdentity: null,
    };

    componentDidMount() {
      this.props.mightFail(getUsers(this.props.id), (users) => this.setState({users}), showError);
    }

    isUserAlreadyAdded = (id) => this.state.users.some((user) => user.id === id);

    addUser = () => {
      this.getSelectedUser(({id, type, name, memberCount}) => {
        const newId = `${type.toUpperCase()}:${id}`;
        const newIdentity = {id: newId, identity: {id, name, type, memberCount}};

        if (!this.isUserAlreadyAdded(newId)) {
          this.setState(({users}) => ({
            users: update(users, {$push: [newIdentity]}),
            selectedIdentity: null,
          }));
        } else {
          this.setState({alreadyExists: true});
        }
      });
    };

    getSelectedUser = (cb) => {
      const {id, name} = this.state.selectedIdentity;
      if (!name) {
        return this.props.mightFail(getUser(id), cb, showError);
      }

      cb(this.state.selectedIdentity);
    };

    removeUser = (id) =>
      this.setState(({users}) => ({
        users: users.filter((user) => user.id !== id),
      }));

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
      const {loading, users, selectedIdentity, alreadyExists} = this.state;
      const isValid = users && users.length > 0;

      return (
        <Modal open={id} onClose={this.close} onConfirm={this.onConfirm} className="UsersModal">
          <Modal.Header>{t('common.editAccess')}</Modal.Header>
          <Modal.Content>
            <p className="description">{t('events.permissions.description')}</p>
            <div className="inputGroup">
              <Labeled className="userTypeahead" label={t('home.userTitle')}>
                <UserTypeahead
                  selectedIdentity={selectedIdentity}
                  onChange={(selectedIdentity) =>
                    this.setState({selectedIdentity, alreadyExists: false})
                  }
                />
              </Labeled>
              <Button disabled={!selectedIdentity} className="confirm" onClick={this.addUser}>
                {t('common.add')}
              </Button>
            </div>
            {alreadyExists && selectedIdentity.type === 'user' && (
              <Message error>{t('home.roles.existing-user-error')}</Message>
            )}
            {alreadyExists && selectedIdentity.type === 'group' && (
              <Message error>{t('home.roles.existing-group-error')}</Message>
            )}
            <EntityList
              embedded
              name={t('events.permissions.whoHasAccess')}
              empty={t('events.permissions.noUsers')}
              isLoading={!users}
              data={
                users &&
                users.map((user) => {
                  const {id, identity} = user;

                  return {
                    className: identity.type,
                    icon: identity.type === 'group' ? 'user-group' : 'user',
                    type: formatType(identity.type),
                    name: identity.name || identity.id,
                    meta: [
                      identity.type === 'group' &&
                        `${identity.memberCount} ${t(
                          'common.user.' + (identity.memberCount > 1 ? 'label-plural' : 'label')
                        )}`,
                    ],
                    actions: [
                      {
                        icon: 'delete',
                        text: t('common.remove'),
                        action: () => this.removeUser(id),
                      },
                    ],
                  };
                })
              }
            />
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
);

function formatType(type) {
  switch (type) {
    case 'user':
      return t('common.user.label');
    case 'group':
      return t('common.user-group.label');
    default:
      return t('common.id');
  }
}
