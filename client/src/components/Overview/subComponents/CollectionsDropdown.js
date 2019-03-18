import React from 'react';
import {Dropdown} from 'components';
import {withStore} from '../OverviewStore';

export default withStore(function CollectionsDropdown({
  store: {collections},
  entity,
  toggleEntityCollection,
  currentCollection,
  setCollectionToUpdate,
  entitiesCollections
}) {
  const entityCollections = entitiesCollections[entity.id] || [];
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
      {reorderedCollections.map(collection => {
        const isEntityInCollection = entityCollections.some(
          entityCollections => entityCollections.id === collection.id
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
      })}
      <Dropdown.Option onClick={() => setCollectionToUpdate({data: {entities: [entity.id]}})}>
        Add to new Collection...
      </Dropdown.Option>
    </Dropdown>
  );
});
