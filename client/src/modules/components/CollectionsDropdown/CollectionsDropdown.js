/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Dropdown} from 'components';
import './CollectionsDropdown.scss';
import classnames from 'classnames';

export default function CollectionsDropdown({
  collections,
  toggleEntityCollection,
  entity,
  entityCollections = [],
  setCollectionToUpdate,
  currentCollection
}) {
  const collectionsCount = entityCollections.length;
  let label = <span className="noCollection">Add to Collection</span>;
  if (collectionsCount) {
    label = `In ${collectionsCount} Collection${collectionsCount !== 1 ? 's' : ''}`;
  }

  let reorderedCollections = collections;
  if (currentCollection) {
    reorderedCollections = collections.filter(collection => collection.id !== currentCollection.id);
    reorderedCollections.unshift(currentCollection);
  }
  return (
    <Dropdown
      className={classnames('CollectionsDropdown')}
      label={label}
      fixedOptions={[
        <Dropdown.Option onClick={() => setCollectionToUpdate({data: {entities: [entity.id]}})}>
          Add to new Collection...
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
