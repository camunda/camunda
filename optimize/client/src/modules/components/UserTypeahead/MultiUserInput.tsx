/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo, useRef, useState} from 'react';
import {FilterableMultiSelect} from '@carbon/react';

import {t} from 'translation';
import {getRandomId} from 'services';

import {
  itemToString,
  itemToElement,
  identityToItem,
  getItems,
  getSelectedIdentity,
  Item,
} from './service';
import useLoadIdentities from './useLoadIdentities';
import {UserInputProps} from './UserTypeahead';

export default function MultiUserInput({
  users = [],
  collectionUsers = [],
  onAdd,
  fetchUsers,
  optionsOnly,
  onRemove,
  onClear,
  excludeGroups = false,
  titleText,
}: UserInputProps): JSX.Element {
  const {loading, setLoading, identities, loadNewValues} = useLoadIdentities({
    fetchUsers,
    excludeGroups,
  });
  const [textValue, setTextValue] = useState('');
  const multiSelectRef = useRef<HTMLElement>(null);
  const selectedUserItems = useMemo(
    () => users.map((user) => identityToItem(user.identity)),
    [users]
  );
  const items = getItems(
    loading,
    textValue,
    identities,
    selectedUserItems,
    users,
    collectionUsers,
    optionsOnly
  );

  useEffect(() => {
    loadNewValues('');

    function handleInputChange(evt: KeyboardEvent) {
      const excludedKeys = [' ', 'Enter', 'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'];

      if (!excludedKeys.includes(evt.key)) {
        const text = (evt?.target as HTMLInputElement)?.value;
        setTextValue(text);
        loadNewValues(text, 800);
      }
    }

    function handleBlur() {
      setTextValue('');
      loadNewValues('');
      setLoading(false);
    }

    const input = multiSelectRef.current?.querySelector('input');

    if (input) {
      input.addEventListener('keyup', handleInputChange);
      input.addEventListener('blur', handleBlur);
    }

    return () => {
      if (input) {
        input.removeEventListener('keyup', handleInputChange);
        input.removeEventListener('blur', handleBlur);
      }
    };
  }, [loadNewValues, setLoading]);

  function handleSelect(item: Item | null) {
    if (!item) {
      return;
    }

    const userToRemove = users.find((user) => user.id === item.id);

    if (userToRemove) {
      onRemove(userToRemove.id);
    } else {
      addIdentity(item.id);
    }
  }

  function addIdentity(id: string | null) {
    const selectedIdentity = getSelectedIdentity(id, identities, users, collectionUsers);
    if (selectedIdentity) {
      onAdd(selectedIdentity);
    }
  }

  return (
    <FilterableMultiSelect
      titleText={titleText}
      id={getRandomId()}
      className="MultiUserInput"
      placeholder={t('common.collection.addUserModal.searchPlaceholder')}
      ref={multiSelectRef}
      // disable the internal sorting since we have the data sorted by default
      sortItems={(items) => items}
      initialSelectedItems={selectedUserItems}
      downshiftProps={{
        onSelect: handleSelect,
      }}
      onChange={({selectedItems}) => {
        if (selectedItems.length === 0) {
          onClear();
        }
      }}
      items={items}
      itemToString={(item) => {
        // This is a workaround to prevent the itemToString from being called with an array
        // This happens on initial render
        if (Array.isArray(item)) {
          return '';
        }
        return itemToString(item);
      }}
      itemToElement={(item) => itemToElement(item, textValue)}
    />
  );
}
