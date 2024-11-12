/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Component} from 'react';
import {Button} from '@carbon/react';
import {Edit, TrashCan, User} from '@carbon/icons-react';

import {t} from 'translation';
import {Deleter, BulkDeleter, EntityList, EmptyState} from 'components';
import {showError} from 'notifications';
import {withErrorHandling} from 'HOC';
import {getOptimizeProfile} from 'config';

import AddUserModal from './modals/AddUserModal';
import EditUserModal from './modals/EditUserModal';
import {addUser, editUser, removeUser, getUsers, removeUsers} from './service';
import {formatRole} from './formatters';

import './UserList.scss';

export default withErrorHandling(
  class UserList extends Component {
    state = {
      users: null,
      deleting: null,
      editing: null,
      addingUser: false,
      optimizeProfile: null,
    };

    async componentDidMount() {
      this.getUsers();
      this.setState({optimizeProfile: await getOptimizeProfile()});
    }

    getUsers = () => {
      this.props.mightFail(
        getUsers(this.props.collection),
        (users) => this.setState({users}),
        showError
      );
    };

    updateList = () => {
      this.getUsers();
      this.props.onChange();
    };

    openAddUserModal = () => this.setState({addingUser: true});
    addUsers = (roles) => {
      this.closeAddUserModal();
      this.props.mightFail(addUser(this.props.collection, roles), this.updateList, showError);
    };
    closeAddUserModal = () => this.setState({addingUser: false});

    openEditUserModal = (editing) => this.setState({editing});
    editUser = (role) => {
      this.props.mightFail(
        editUser(this.props.collection, this.state.editing.id, role),
        this.updateList,
        showError
      );
      this.closeEditUserModal();
    };
    closeEditUserModal = () => this.setState({editing: null});

    render() {
      const {users, deleting, editing, addingUser, optimizeProfile} = this.state;
      const {readOnly, collection} = this.props;

      const columns = [t('common.name'), t('home.roles.role')];

      return (
        <div className="UserList">
          <EntityList
            action={
              !readOnly && (
                <Button kind="primary" onClick={this.openAddUserModal}>
                  {t('common.add')}
                </Button>
              )
            }
            bulkActions={
              !readOnly && [
                <BulkDeleter
                  type="remove"
                  deleteEntities={async (selectedUsers) =>
                    await removeUsers(collection, selectedUsers)
                  }
                />,
              ]
            }
            onChange={this.updateList}
            emptyStateComponent={<EmptyState icon={<User />} title={t('common.notFound')} />}
            isLoading={!users}
            headers={columns}
            rows={
              users &&
              users.map((user) => {
                const {identity, role} = user;

                const numberOfManagers = users.filter(({role}) => role === 'manager').length;
                const isLastManager = role === 'manager' && numberOfManagers === 1;
                const meta = [formatRole(role)];

                return {
                  id: user.id,
                  entityType: 'user',
                  className: identity.type,
                  icon: <User />,
                  type: formatType(identity.type),
                  name: identity.name || identity.id,
                  meta,
                  actions: !readOnly &&
                    !isLastManager && [
                      {
                        icon: <Edit />,
                        text: t('common.edit'),
                        action: () => this.openEditUserModal(user),
                      },
                      {
                        icon: <TrashCan />,
                        text: t('common.remove'),
                        action: () => this.setState({deleting: user}),
                      },
                    ],
                };
              })
            }
          />
          <Deleter
            type={deleting?.identity?.type}
            entity={deleting?.identity}
            onDelete={this.updateList}
            onClose={() => this.setState({deleting: null})}
            deleteEntity={() => removeUser(collection, deleting.id)}
            deleteText={
              deleting?.identity?.type &&
              t('common.removeEntity', {
                entity: t('common.deleter.types.' + deleting.identity.type),
              })
            }
            descriptionText={t('home.roles.deleteWarning', {
              name:
                (deleting &&
                  deleting.identity &&
                  (deleting.identity.name || deleting.identity.id)) ||
                '',
              type: deleting && deleting.identity && formatType(deleting.identity.type),
            })}
          />
          <AddUserModal
            optimizeProfile={optimizeProfile}
            open={addingUser}
            existingUsers={users}
            onClose={this.closeAddUserModal}
            onConfirm={this.addUsers}
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

function formatType(type) {
  if (!type) {
    return t('home.types.unknown');
  }

  return t('common.user.label');
}
