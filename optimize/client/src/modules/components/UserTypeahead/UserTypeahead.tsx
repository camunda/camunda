/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ComponentType, ReactNode} from 'react';
import update from 'immutability-helper';
import {DropdownSkeleton} from '@carbon/react';

import {t} from 'translation';
import {showError} from 'notifications';
import {useErrorHandling} from 'hooks';

import {getUser, User, getUserId, Identity} from './service';
import MultiUserInput from './MultiUserInput';
import SingleUserInput from './SingleUserInput';

export interface UserInputProps {
  titleText?: ReactNode;
  users: User[];
  collectionUsers?: User[];
  onAdd: (value: {id: string} | Identity) => void;
  fetchUsers?: (
    query: string,
    excludeGroups?: boolean
  ) => Promise<{total: number; result: Identity[]}>;
  optionsOnly?: boolean;
  onRemove: (id: string) => void;
  onClear: () => void;
  excludeGroups?: boolean;
}

interface UserTypeaheadProps
  extends Partial<Omit<UserInputProps, 'users' | 'collectionUsers' | 'onChange'>> {
  collectionUsers?: User[] | null;
  users: User[] | null;
  onChange: (users: User[]) => void;
  singleUser?: boolean;
}

export default function UserTypeahead({
  users = [],
  collectionUsers = [],
  onChange,
  singleUser = false,
  ...props
}: UserTypeaheadProps) {
  const {mightFail} = useErrorHandling();

  if (!users || !collectionUsers) {
    return <DropdownSkeleton />;
  }

  const getSelectedUser = (user: {id: string} | Identity, cb: (user: Identity) => void) => {
    if (!('name' in user)) {
      return mightFail(
        getUser(user.id),
        (user) => {
          const exists = (users: User[]) =>
            users.some((existingUser) => existingUser.id === getUserId(user.id));

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

  const addUser = (user: {id: string} | Identity) => {
    getSelectedUser(user, ({id, name, email}) => {
      const newId = getUserId(id);
      const newIdentity: User = {id: newId, identity: {id, name, email}};
      onChange(update(users, {$push: [newIdentity]}));
    });
  };

  const removeUser = (id: string) => onChange(users.filter((user) => user.id !== id));

  const Component: ComponentType<UserInputProps> = singleUser ? SingleUserInput : MultiUserInput;

  return (
    <Component
      {...props}
      users={users}
      collectionUsers={collectionUsers}
      onAdd={addUser}
      onRemove={removeUser}
      onClear={() => onChange([])}
    />
  );
}
