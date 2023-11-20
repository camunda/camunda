/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Component} from 'react';
import {Button} from '@carbon/react';

import {t} from 'translation';
import {EntityList, Deleter, BulkDeleter} from 'components';
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
      const title =
        optimizeProfile === 'platform' ? t('home.userGroupsTitle') : t('home.userTitle');

      const columns = [t('common.name'), t('home.roles.role')];
      if (optimizeProfile === 'platform') {
        columns.splice(1, 0, t('home.members'));
      }

      return (
        <div className="UserList">
          <EntityList
            name={title}
            action={(bulkActive) =>
              !readOnly && (
                <Button
                  kind={bulkActive ? 'tertiary' : 'primary'}
                  onClick={this.openAddUserModal}
                  size="md"
                >
                  {t('common.add')}
                </Button>
              )
            }
            bulkActions={[
              <BulkDeleter
                type="remove"
                deleteEntities={async (selectedUsers) =>
                  await removeUsers(collection, selectedUsers)
                }
              />,
            ]}
            onChange={this.updateList}
            empty={t('common.notFound')}
            isLoading={!users}
            columns={columns}
            data={
              users &&
              users.map((user) => {
                const {identity, role} = user;

                const numberOfManagers = users.filter(({role}) => role === 'manager').length;
                const isLastManager = role === 'manager' && numberOfManagers === 1;
                const meta = [formatRole(role)];

                if (optimizeProfile === 'platform') {
                  meta.unshift(
                    identity.type === 'group' &&
                      `${identity.memberCount} ${t(
                        'common.user.' + (identity.memberCount > 1 ? 'label-plural' : 'label')
                      )}`
                  );
                }

                return {
                  id: user.id,
                  entityType: 'user',
                  className: identity.type,
                  icon: identity.type === 'group' ? 'user-group' : 'user',
                  type: formatType(identity.type),
                  name: identity.name || identity.id,
                  meta,
                  actions: !readOnly &&
                    !isLastManager && [
                      {
                        icon: 'edit',
                        text: t('common.edit'),
                        action: () => this.openEditUserModal(user),
                      },
                      {
                        icon: 'delete',
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
  switch (type) {
    case 'user':
      return t('common.user.label');
    case 'group':
      return t('common.user-group.label');
    default:
      return t('home.types.unknown');
  }
}
