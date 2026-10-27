/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {get} from 'request';
import {formatters} from 'services';

export interface Identity {
  id: string | null;
  name: string | null;
  email?: string;
  type?: string;
}

export interface User {
  id: string;
  identity: Identity;
  type?: string;
}

export interface Item {
  id: string;
  label: string;
  subText?: string | null;
  disabled?: boolean;
}

export async function searchIdentities(
  terms: string,
  excludeUserGroups: boolean
): Promise<{total: number; result: Identity[]}> {
  const response = await get('api/identity/search', {terms, excludeUserGroups});
  return await response.json();
}

export async function getUser(id: string): Promise<Identity> {
  const response = await get(`api/identity/${id}`);
  return await response.json();
}

export function getUserId(id: string | null): string {
  if (id?.startsWith('USER:')) {
    return id;
  }
  return `USER:${id}`;
}

export function itemToElement(item: Item | null, textValue: string): JSX.Element {
  if (!item) {
    return <></>;
  }

  if (item.id === 'loading') {
    return <p className="cds--checkbox-label-text cds--skeleton" />;
  }

  const {label, subText, id} = item;
  return (
    <span id={id}>
      <span className="label">{formatters.getHighlightedText(label, textValue)}</span>
      {subText && (
        <span className="subText">{formatters.getHighlightedText(subText, textValue, true)}</span>
      )}
    </span>
  );
}

export function getItems(
  loading: boolean,
  textValue: string,
  identities: Identity[],
  selectedUsers: Item[],
  users: User[],
  collectionUsers: User[],
  optionsOnly?: boolean
): Item[] {
  if (loading) {
    return [{id: 'loading', label: textValue, disabled: true}];
  }

  const filteredIdentities = identities.filter((identity) =>
    filterOutSelectedIdentity(identity, users, collectionUsers)
  );
  const items = filteredIdentities.map(identityToItem);

  items.unshift(...selectedUsers);

  if (!optionsOnly && textValue && !identities.some((item) => item.id === textValue)) {
    items.unshift({id: textValue, label: textValue});
  }

  return items;
}

function filterOutSelectedIdentity(
  identity: Identity,
  users: User[],
  collectionUsers: User[]
): boolean {
  const exists = (users: User[]) => users.some((user) => user.id === getUserId(identity.id));

  return !exists(users) && !exists(collectionUsers);
}

export function identityToItem(identity: Identity): Item {
  const {label, subText} = formatTypeaheadOption(identity);

  return {
    id: getUserId(identity.id),
    label,
    subText,
  };
}

function formatTypeaheadOption({name, email, id}: Identity): {
  label: string;
  subText: string | null;
} {
  let subText: string | null = null;
  if (name && email) {
    subText = email;
  }

  return {
    label: name || email || id || '',
    subText,
  };
}

export function getSelectedIdentity(
  id: string | null,
  identities: Identity[],
  users: User[],
  collectionUsers: User[]
) {
  if (id || id === null) {
    const selectedIdentity = identities
      .filter((identity) => filterOutSelectedIdentity(identity, users, collectionUsers))
      .find((identity) => getUserId(identity.id) === id);

    if (selectedIdentity) {
      return selectedIdentity;
    } else if (id) {
      return {id};
    }
  }
}
