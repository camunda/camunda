/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import update from 'immutability-helper';

import {withErrorHandling, WithErrorHandlingProps} from 'HOC';
import {t} from 'translation';
import {showError} from 'notifications';

import MultiUserInput, {MultiUserInputProps} from './MultiUserInput';
import {getUser, User} from './service';
import {DropdownSkeleton} from '@carbon/react';

interface UserTypeaheadProps
  extends WithErrorHandlingProps,
    Partial<Omit<MultiUserInputProps, 'users' | 'collectionUsers' | 'onChange'>> {
  collectionUsers?: User[] | null;
  users: User[] | null;
  onChange: (users: User[]) => void;
  loading?: boolean;
}

export function UserTypeahead({
  users = [],
  collectionUsers = [],
  onChange,
  mightFail,
  loading,
  ...props
}: UserTypeaheadProps) {
  if (!users || !collectionUsers || loading) {
    return <DropdownSkeleton />;
  }

  const getSelectedUser = (
    user: {id: string} | User['identity'],
    cb: (user: User['identity']) => void
  ) => {
    if (!('name' in user)) {
      return mightFail(
        getUser(user.id),
        (user) => {
          const {type, id} = user;
          const exists = (users: User[]) =>
            users.some((user) => user.id === `${type.toUpperCase()}:${id}`);

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

  const addUser = (user: {id: string} | User['identity']) => {
    getSelectedUser(user, ({id, type, name, memberCount, email}) => {
      const newId = `${type.toUpperCase()}:${id}`;
      const newIdentity: User = {id: newId, identity: {id, name, type, memberCount, email}};
      onChange(update(users, {$push: [newIdentity]}));
    });
  };

  const removeUser = (id: string) => onChange(users.filter((user) => user.id !== id));

  return (
    <MultiUserInput
      {...props}
      users={users}
      collectionUsers={collectionUsers}
      onAdd={addUser}
      onRemove={removeUser}
      onClear={() => onChange([])}
    />
  );
}

export default withErrorHandling(UserTypeahead);
