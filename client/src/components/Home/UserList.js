/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {t} from 'translation';
import {Button, EntityList, Deleter} from 'components';
import {showError} from 'notifications';
import {withErrorHandling} from 'HOC';

import AddUserModal from './modals/AddUserModal';
import EditUserModal from './modals/EditUserModal';

import {addUser, editUser, removeUser, getUsers} from './service';

import {ReactComponent as UserIcon} from './icons/user.svg';
import {ReactComponent as GroupIcon} from './icons/usergroup.svg';

import './UserList.scss';

export default withErrorHandling(
  class UserList extends React.Component {
    state = {
      users: null,
      deleting: null,
      editing: null,
      addingUser: false
    };

    componentDidMount() {
      this.getUsers();
    }

    getUsers = () => {
      this.props.mightFail(
        getUsers(this.props.collection),
        users => this.setState({users}),
        showError
      );
    };

    updateList = () => {
      this.getUsers();
      this.props.onChange();
    };

    openAddUserModal = () => this.setState({addingUser: true});
    addUser = (name, type, role) => {
      this.closeAddUserModal();
      this.props.mightFail(
        addUser(this.props.collection, name, type, role),
        this.updateList,
        showError
      );
    };
    closeAddUserModal = () => this.setState({addingUser: false});

    openEditUserModal = editing => this.setState({editing});
    editUser = role => {
      this.props.mightFail(
        editUser(this.props.collection, this.state.editing.id, role),
        this.updateList,
        showError
      );
      this.closeEditUserModal();
    };
    closeEditUserModal = () => this.setState({editing: null});

    render() {
      const {users, deleting, editing, addingUser} = this.state;
      const {readOnly, collection} = this.props;

      return (
        <div className="UserList">
          <EntityList
            name={t('home.userTitle')}
            action={!readOnly && <Button onClick={this.openAddUserModal}>{t('common.add')}</Button>}
            empty={t('common.notFound')}
            isLoading={!users}
            data={
              users &&
              users.map(user => {
                const {identity, role} = user;

                const numberOfManagers = users.filter(({role}) => role === 'manager').length;
                const isLastManager = role === 'manager' && numberOfManagers === 1;

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
                  meta2: formatRole(role),
                  actions: !readOnly &&
                    !isLastManager && [
                      {
                        icon: 'edit',
                        text: t('common.edit'),
                        action: () => this.openEditUserModal(user)
                      },
                      {
                        icon: 'delete',
                        text: t('common.delete'),
                        action: () => this.setState({deleting: user})
                      }
                    ]
                };
              })
            }
          />
          <Deleter
            entity={deleting && deleting.identity}
            onDelete={this.updateList}
            onClose={() => this.setState({deleting: null})}
            deleteEntity={() => removeUser(collection, deleting.id)}
          />
          <AddUserModal
            open={addingUser}
            existingUsers={users}
            onClose={this.closeAddUserModal}
            onConfirm={this.addUser}
          />
          {editing && (
            <EditUserModal
              initialRole={editing.role}
              identity={editing.identity}
              onClose={this.closeEditUserModal}
              onConfirm={this.editUser}
            />
          )}
        </div>
      );
    }
  }
);

function getEntityIcon(type) {
  switch (type) {
    case 'user':
      return <UserIcon />;
    case 'group':
      return <GroupIcon />;
    default:
      return <UserIcon />;
  }
}

function formatType(type) {
  switch (type) {
    case 'user':
      return t('common.user.label');
    case 'group':
      return t('common.user-group.label');
    default:
      return t('home.types.unknown');
  }
}

function formatRole(role) {
  return t('home.roles.' + role);
}
