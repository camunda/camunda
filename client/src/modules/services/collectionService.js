/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {removeEntityFromCollection, addEntityToCollection} from './entityService';

export function toggleEntityCollection(loadCollections) {
  return async function(entity, collection, isRemove) {
    if (isRemove) {
      await removeEntityFromCollection(entity.id, collection.id);
    } else {
      await addEntityToCollection(entity.id, collection.id);
    }

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
