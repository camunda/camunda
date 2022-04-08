/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import update from 'immutability-helper';

import {withErrorHandling} from 'HOC';
import {t} from 'translation';
import {showError} from 'notifications';

import MultiUserInput from './MultiUserInput';
import {getUser} from './service';

export function UserTypeahead({
  users,
  collectionUsers = [],
  onChange,
  mightFail,
  fetchUsers,
  optionsOnly,
  excludeGroups = false,
}) {
  const getSelectedUser = (user, cb) => {
    const {id, name} = user;
    if (!name) {
      return mightFail(
        getUser(id),
        (user) => {
          const {type, id} = user;
          const exists = (users) => users.some((user) => user.id === `${type.toUpperCase()}:${id}`);

          if (exists(users)) {
            return showError(t('home.roles.existing-identity'));
          }

          if (exists(collectionUsers)) {
            return showError(
              t('home.roles.existing-identity') + ' ' + t('home.roles.inCollection')
            );
          }

          cb(user);
        },
        showError
      );
    }

    cb(user);
  };

  const addUser = (user) => {
    getSelectedUser(user, ({id, type, name, memberCount}) => {
      const newId = `${type.toUpperCase()}:${id}`;
      const newIdentity = {id: newId, identity: {id, name, type, memberCount}};
      onChange(update(users, {$push: [newIdentity]}));
    });
  };

  const removeUser = (id) => onChange(users.filter((user) => user.id !== id));

  return (
    <MultiUserInput
      users={users}
      collectionUsers={collectionUsers}
      fetchUsers={fetchUsers}
      onAdd={addUser}
      onRemove={removeUser}
      onClear={() => onChange([])}
      optionsOnly={optionsOnly}
      excludeGroups={excludeGroups}
    />
  );
}

export default withErrorHandling(UserTypeahead);
