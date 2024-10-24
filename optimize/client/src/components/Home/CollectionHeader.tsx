/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  OverflowMenu,
  OverflowMenuItem,
  SkeletonIcon,
  SkeletonText,
  Stack,
  Tag,
  TagSkeleton,
} from '@carbon/react';
import {Folder} from '@carbon/icons-react';

import {t} from 'translation';
import {EntityListEntity} from 'types';

import {formatRole} from './formatters';

import './CollectionHeader.scss';

interface CollectionHeaderProps {
  collection: EntityListEntity | null;
  isLoading?: boolean;
  onEditStart: () => void;
  onCopy: (collection: EntityListEntity) => void;
  onDelete: (collection: EntityListEntity) => void;
}

export default function CollectionHeader({
  collection,
  isLoading,
  onEditStart,
  onCopy,
  onDelete,
}: CollectionHeaderProps) {
  const collectionEntity: EntityListEntity | undefined = collection
    ? {...collection, entityType: 'collection'}
    : undefined;

  return (
    <Stack gap={6} className="CollectionHeader" orientation="horizontal">
      <Folder size="24" />
      {collectionEntity && (
        <>
          <span title={collectionEntity.name} className="text">
            {collectionEntity.name}
          </span>
          {collectionEntity.currentUserRole === 'manager' && (
            <OverflowMenu>
              <OverflowMenuItem itemText={t('common.edit')} onClick={onEditStart} />
              <OverflowMenuItem
                itemText={t('common.copy')}
                onClick={() => onCopy(collectionEntity)}
              />
              <OverflowMenuItem
                isDelete
                itemText={t('common.delete')}
                onClick={() => onDelete(collectionEntity)}
              />
            </OverflowMenu>
          )}
          <Tag className="role" type="blue">
            {formatRole(collectionEntity.currentUserRole)}
          </Tag>
        </>
      )}
      {!collectionEntity && isLoading && (
        <>
          <SkeletonText className="skeletonText" heading />
          <SkeletonIcon />
          <TagSkeleton />
        </>
      )}
    </Stack>
  );
}
