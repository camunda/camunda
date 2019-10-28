/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {t} from 'translation';
import {LoadingIndicator, Icon, Dropdown, Input, ConfirmationModal, Button} from 'components';
import {showError} from 'notifications';
import {withErrorHandling} from 'HOC';

import AddUserModal from './AddUserModal';
import EditUserModal from './EditUserModal';
import ListItem from './ListItem';

import {addUser, editUser, removeUser} from './service';

import {ReactComponent as UserIcon} from './icons/user.svg';
import {ReactComponent as GroupIcon} from './icons/usergroup.svg';

import './UserList.scss';

export default withErrorHandling(
  class UserList extends React.Component {
    state = {
      deleting: null,
      editing: null,
      addingUser: false,
      deleteInProgress: false,
      searchQuery: ''
    };

    confirmDelete = entity => {
      this.setState({deleting: entity});
    };

    resetDelete = () => this.setState({deleting: null, deleteInProgress: false});

    deleteEntity = () => {
      const {id} = this.state.deleting;
      this.resetDelete();
      this.setState({deleteInProgress: true});
      this.props.mightFail(removeUser(this.props.collection, id), this.props.onChange, error => {
        showError(error);
        this.setState({deleteInProgress: false});
      });
    };

    openAddUserModal = () => this.setState({addingUser: true});
    addUser = (name, type, role) => {
      this.closeAddUserModal();
      this.props.mightFail(
        addUser(this.props.collection, name, type, role),
        this.props.onChange,
        showError
      );
    };
    closeAddUserModal = () => this.setState({addingUser: false});

    openEditUserModal = editing => this.setState({editing});
    editUser = role => {
      this.props.mightFail(
        editUser(this.props.collection, this.state.editing.id, role),
        this.props.onChange,
        showError
      );
      this.closeEditUserModal();
    };
    closeEditUserModal = () => this.setState({editing: null});

    render() {
      const {deleting, editing, deleteInProgress, searchQuery, addingUser} = this.state;
      const {readOnly} = this.props;

      return (
        <div className="UserList">
          <div className="header">
            <h1>{t('home.userTitle')}</h1>
            <div className="searchContainer">
              <Icon className="searchIcon" type="search" />
              <Input
                required
                type="text"
                className="searchInput"
                placeholder={t('home.search.name')}
                value={searchQuery}
                onChange={({target: {value}}) => this.setState({searchQuery: value})}
                onClear={() => this.setState({searchQuery: ''})}
              />
            </div>
            {!readOnly && <Button onClick={this.openAddUserModal}>{t('common.add')}</Button>}
          </div>
          <div className="content">
            <ul>{this.renderList()}</ul>
          </div>
          <ConfirmationModal
            open={deleting}
            onClose={this.resetDelete}
            onConfirm={this.deleteEntity}
            entityName={deleting && deleting.identity.id}
            loading={deleteInProgress}
          />
          <AddUserModal
            open={addingUser}
            existingUsers={this.props.data}
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

    renderList() {
      const {data, readOnly} = this.props;

      if (data === null) {
        return <LoadingIndicator />;
      }

      const searchFilteredData = this.props.data.filter(({identity: {id, name}}) =>
        (name || id).toLowerCase().includes(this.state.searchQuery.toLowerCase())
      );

      if (searchFilteredData.length === 0) {
        return <div className="empty">{t('common.notFound')}</div>;
      }

      const numberOfManagers = this.props.data.filter(({role}) => role === 'manager').length;

      return searchFilteredData.map(entity => {
        const {id, identity, role} = entity;

        const isLastManager = role === 'manager' && numberOfManagers === 1;

        return (
          <ListItem key={id} className={identity.type}>
            <ListItem.Section className="icon">{getEntityIcon(identity.type)}</ListItem.Section>
            <ListItem.Section className="name">
              <div className="type">{formatType(identity.type)}</div>
              <div className="entityName">{identity.name || identity.id}</div>
            </ListItem.Section>
            <ListItem.Section className="containedEntities">
              {identity.type === 'group' && (
                <>
                  {identity.memberCount}{' '}
                  {t('common.user.' + (identity.memberCount > 1 ? 'label-plural' : 'label'))}
                </>
              )}
            </ListItem.Section>
            <ListItem.Section className="role">{formatRole(role)}</ListItem.Section>
            {!readOnly && !isLastManager && (
              <div className="contextMenu">
                <Dropdown label={<Icon type="overflow-menu-vertical" size="24px" />}>
                  <Dropdown.Option onClick={() => this.openEditUserModal(entity)}>
                    <Icon type="edit" />
                    {t('common.edit')}
                  </Dropdown.Option>
                  <Dropdown.Option onClick={() => this.confirmDelete(entity)}>
                    <Icon type="delete" />
                    {t('common.delete')}
                  </Dropdown.Option>
                </Dropdown>
              </div>
            )}
          </ListItem>
        );
      });
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
