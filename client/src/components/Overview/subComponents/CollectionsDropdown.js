import React from 'react';
import {Dropdown} from 'components';

export default function CollectionsDropdown({
  collections,
  entity,
  toggleEntityCollection,
  currentCollection,
  entityCollections = []
}) {
  const collectionsCount = entityCollections.length;
  let label = <span className="noCollection">Add to Collection</span>;
  if (collectionsCount) {
    label = `${collectionsCount} Collection${collectionsCount !== 1 ? 's' : ''}`;
  }

  let reorderedCollections = collections;
  if (currentCollection) {
    reorderedCollections = collections.filter(collection => collection.id !== currentCollection.id);
    reorderedCollections.unshift(currentCollection);
  }

  return (
    <Dropdown className="entityCollections" label={label}>
      {collections.length > 0 ? (
        reorderedCollections.map(collection => {
          const isEntityInCollection = entityCollections.some(
            entityCollection => entityCollection.id === collection.id
          );
          return (
            <Dropdown.Option
              key={collection.id}
              checked={isEntityInCollection}
              onClick={toggleEntityCollection(entity, collection, isEntityInCollection)}
            >
              {collection.name}
            </Dropdown.Option>
          );
        })
      ) : (
        <Dropdown.Option disabled>No collections found</Dropdown.Option>
      )}
    </Dropdown>
  );
}
