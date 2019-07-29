/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Dropdown} from 'components';
import './CollectionsDropdown.scss';
import {t} from 'translation';

export default function CollectionsDropdown({
  collections,
  toggleEntityCollection,
  entity,
  entityCollections = [],
  setCollectionToUpdate,
  currentCollection
}) {
  const collectionsCount = entityCollections.length;
  let label = <span className="noCollection">{t('common.collection.dropdown.default')}</span>;
  if (collectionsCount) {
    label = t('common.collection.dropdown.in', {
      collectionsCount,
      collection: t(`common.collection.label${collectionsCount !== 1 ? '-plural' : ''}`)
    });
  }

  let reorderedCollections = collections;
  if (currentCollection) {
    reorderedCollections = collections.filter(collection => collection.id !== currentCollection.id);
    reorderedCollections.unshift(currentCollection);
  }
  return (
    <Dropdown
      label={label}
      className="CollectionsDropdown"
      fixedOptions={[
        <Dropdown.Option onClick={() => setCollectionToUpdate({data: {entities: [entity.id]}})}>
          {t('common.collection.dropdown.add')}
        </Dropdown.Option>
      ]}
    >
      {reorderedCollections.map(collection => {
        const isEntityInCollection = entityCollections.some(
          entityCollections => entityCollections.id === collection.id
        );
        return (
          <Dropdown.Option
            key={collection.id}
            checked={isEntityInCollection}
            onClick={evt => toggleEntityCollection(entity, collection, isEntityInCollection)}
          >
            {collection.name}
          </Dropdown.Option>
        );
      })}
    </Dropdown>
  );
}
