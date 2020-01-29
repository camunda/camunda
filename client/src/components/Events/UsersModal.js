/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Modal, Button, UserTypeahead, EntityList, Icon, Message, Labeled} from 'components';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {getUsers, updateUsers} from './service';
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
      selectedIdentity: null
    };

    componentDidMount() {
      this.getUsers();
    }

    getUsers = () => {
      this.props.mightFail(getUsers(this.props.id), users => this.setState({users}), showError);
    };

    isUserExist = id => this.state.users.some(user => user.id === id);

    addUser = () => {
      const {id, type, name, memberCount} = this.state.selectedIdentity;

      const newId = `${type}:${id}`;
      const newIdentity = {id: newId, identity: {id, name, type, memberCount}};

      if (!this.isUserExist(newId)) {
        this.setState(({users}) => ({
          users: update(users, {$push: [newIdentity]}),
          selectedIdentity: null
        }));
      } else {
        this.setState({alreadyExists: true});
      }
    };

    removeUser = id =>
      this.setState(({users}) => ({
        users: users.filter(user => user.id !== id)
      }));

    onConfirm = () => {
      this.setState({loading: true});
      this.props.mightFail(
        updateUsers(this.props.id, this.state.users),
        () => {
          this.setState({loading: false});
          this.props.onClose(this.state.users.length <= 1);
        },
        error => {
          showError(error);
          this.setState({loading: false});
        }
      );
    };

    render() {
      const {id, onClose} = this.props;
      const {loading, users, selectedIdentity, alreadyExists} = this.state;

      return (
        <Modal open={id} onClose={onClose} onConfirm={this.onConfirm} className="UsersModal">
          <Modal.Header>{t('common.editAccess')}</Modal.Header>
          <Modal.Content>
            <p className="description">{t('events.permissions.description')}</p>
            <div className="inputGroup">
              <Labeled className="userTypeahead" label={t('home.userTitle')}>
                <UserTypeahead
                  selectedIdentity={selectedIdentity}
                  onChange={selectedIdentity =>
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
              name={t('events.permissions.whoHasAccess')}
              empty={t('common.notFound')}
              isLoading={!users}
              data={
                users &&
                users.map(user => {
                  const {id, identity, isOwner} = user;

                  return {
                    className: identity.type,
                    icon: getEntityIcon(identity.type),
                    type: formatType(identity.type),
                    name: identity.name || identity.id,
                    meta1: identity.type === 'group' && (
                      <>
                        {identity.memberCount}{' '}
                        {t('common.user.' + (identity.memberCount > 1 ? 'label-plural' : 'label'))}
                      </>
                    ),
                    meta3: !isOwner && (
                      <Button onClick={() => this.removeUser(id)}>
                        <Icon type="delete" />
                      </Button>
                    )
                  };
                })
              }
            />
          </Modal.Content>
          <Modal.Actions>
            <Button disabled={loading} onClick={onClose}>
              {t('common.cancel')}
            </Button>
            <Button disabled={loading} variant="primary" color="blue" onClick={this.onConfirm}>
              {t('common.save')}
            </Button>
          </Modal.Actions>
        </Modal>
      );
    }
  }
);

function getEntityIcon(type = 'user') {
  return <Icon type={type} size="24" />;
}

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
