/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {updateEntity} from './entityService';

export function toggleEntityCollection(loadCollections) {
  return async function(entity, collection, isRemove) {
    const collectionEntitiesIds = collection.data.entities.map(entity => entity.id);

    const change = {data: {}};
    if (isRemove) {
      change.data.entities = collectionEntitiesIds.filter(id => id !== entity.id);
    } else {
      change.data.entities = [...collectionEntitiesIds, entity.id];
    }

    await updateEntity('collection', collection.id, change);
    await loadCollections();
  };
}

export function getEntitiesCollections(collections) {
  const entitiesCollections = {};

  collections.forEach(collection => {
    collection.data.entities.forEach(entity => {
      if (entitiesCollections[entity.id]) {
        entitiesCollections[entity.id].push(collection);
      } else {
        entitiesCollections[entity.id] = [collection];
      }
    });
  });

  return entitiesCollections;
}
