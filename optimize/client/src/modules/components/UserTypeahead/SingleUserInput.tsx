/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {ComboBox} from '@carbon/react';

import {t} from 'translation';
import {getRandomId} from 'services';

import {itemToElement, getItems, identityToItem, getSelectedIdentity} from './service';
import useLoadIdentities from './useLoadIdentities';
import {UserInputProps} from './UserTypeahead';

export default function SingleUserInput({
  users = [],
  collectionUsers = [],
  onAdd,
  fetchUsers,
  optionsOnly,
  onClear,
  excludeGroups = false,
  titleText,
}: UserInputProps): JSX.Element {
  const {loading, identities, loadNewValues} = useLoadIdentities({
    fetchUsers,
    excludeGroups,
  });
  const [textValue, setTextValue] = useState('');
  const selectedUserItem = users[0] ? identityToItem(users[0].identity) : null;
  const items = getItems(
    loading,
    textValue,
    identities,
    selectedUserItem ? [selectedUserItem] : [],
    users,
    collectionUsers,
    optionsOnly
  );

  function handleInputChange(inputText: string) {
    setTextValue(inputText);
    loadNewValues(inputText, 800);
  }

  function selectIdentity(id: string | null) {
    if (loading || selectedUserItem?.id === id) {
      return;
    }

    const selectedIdentity = getSelectedIdentity(id, identities, users, collectionUsers);
    if (selectedIdentity) {
      onAdd(selectedIdentity);
    }
  }

  return (
    <ComboBox
      titleText={titleText}
      id={getRandomId()}
      className="SingleUserInput"
      onInputChange={handleInputChange}
      placeholder={t('common.collection.addUserModal.searchPlaceholder').toString()}
      selectedItem={selectedUserItem}
      items={items}
      onChange={({selectedItem}) => {
        if (selectedItem) {
          selectIdentity(selectedItem.id);
        } else {
          onClear();
        }
      }}
      itemToString={(item) => {
        if (!item) {
          return '';
        }
        const {label, subText, id} = item;
        return label || subText || id;
      }}
      itemToElement={(item) => itemToElement(item, textValue)}
    />
  );
}
