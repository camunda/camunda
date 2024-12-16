/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {ComboBox, InlineNotification, Toggle} from '@carbon/react';

import {showError} from 'notifications';
import {loadEntities} from 'services';
import {t} from 'translation';
import {useErrorHandling} from 'hooks';
import {EntityListEntity} from 'types';

interface MoveCopyProps {
  parentCollection: string;
  entity: EntityListEntity;
  moving: boolean;
  collection?: EntityListEntity | null;
  setMoving: (moving: boolean) => void;
  setCollection: (collection: EntityListEntity | CollectionsHome | null) => void;
}

export type CollectionsHome = {
  id: null;
  entityType: 'collection';
  name: string;
};

export default function MoveCopy({
  parentCollection,
  entity,
  moving,
  setMoving,
  setCollection,
}: MoveCopyProps) {
  const {mightFail} = useErrorHandling();
  const [availableCollections, setAvailableCollections] = useState<
    (EntityListEntity | CollectionsHome)[]
  >([]);

  useEffect(() => {
    // We allow moving the copy to the collections homepage
    const collectionHome: CollectionsHome = {
      id: null,
      entityType: 'collection',
      name: t('navigation.collections').toString(),
    };

    mightFail(
      loadEntities(),
      (entities) =>
        setAvailableCollections([
          collectionHome,
          ...entities.filter(
            ({entityType, id}) => entityType === 'collection' && id !== parentCollection
          ),
        ]),
      showError
    );
  }, [mightFail, parentCollection]);

  const getMulticopyText = () => {
    const containedReports = entity.data?.subEntityCounts.report;

    if (!containedReports) {
      return undefined;
    }

    const params = {
      entityType: t('dashboard.label').toString(),
      number: containedReports,
    };
    if (containedReports > 1) {
      return t('home.copy.subEntities', params).toString();
    }
    return t('home.copy.subEntity', params).toString();
  };

  const getPlaceholder = () =>
    (availableCollections?.length
      ? t('home.copy.pleaseSelect')
      : t('home.copy.noCollections')
    ).toString();

  const multiTextCopy = getMulticopyText();

  return (
    <>
      <Toggle
        size="sm"
        id="moveToggle"
        labelText={t('home.copy.moveLabel').toString()}
        hideLabel
        toggled={moving}
        onToggle={(checked) => setMoving(checked)}
      />
      {moving && (
        <>
          <ComboBox<Partial<EntityListEntity> | CollectionsHome>
            id="collectionSelection"
            items={availableCollections}
            itemToString={(collection) => (collection as EntityListEntity)?.name}
            onChange={({selectedItem}) => {
              const collection =
                availableCollections.find((col) => col.id === selectedItem?.id) || null;
              setCollection(collection);
            }}
            placeholder={getPlaceholder()}
            disabled={!availableCollections.length}
          />
          {multiTextCopy && (
            <InlineNotification kind="info" hideCloseButton subtitle={getMulticopyText()} />
          )}
        </>
      )}
    </>
  );
}
