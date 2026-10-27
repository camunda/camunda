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

import {itemToElement, identityToItem, getItems, getSelectedIdentity} from './service';
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
  const multiSelectRef = useRef<HTMLDivElement>(null);
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
      placeholder={t('common.collection.addUserModal.searchPlaceholder').toString()}
      ref={multiSelectRef}
      // disable the internal sorting since we have the data sorted by default
      sortItems={(items) => items.slice()}
      initialSelectedItems={selectedUserItems}
      onChange={({selectedItems}) => {
        // If no items are selected, clear the selection
        if (selectedItems.length === 0) {
          onClear();
          return;
        }

        const removedUser = selectedUserItems.find(
          (user) => !selectedItems.some((item) => item.id === user.id)
        );

        if (removedUser) {
          onRemove(removedUser.id);
        }

        const addedUser = selectedItems.find(
          (item) => !selectedUserItems.some((user) => user.id === item.id)
        );

        if (addedUser) {
          addIdentity(addedUser.id);
        }
      }}
      items={items}
      itemToString={(item) => {
        if (!item) {
          return '';
        }

        const {label, subText, id} = item;
        // the FilterableMultiSelect filters items based on the string below
        // we have to include all relevant information to ensure they appear in the menu
        return label + subText + (id || '');
      }}
      itemToElement={(item) => itemToElement(item, textValue)}
    />
  );
}
