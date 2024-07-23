/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  OverflowMenu,
  OverflowMenuItem,
  SkeletonIcon,
  SkeletonText,
  Stack,
  Tag,
  TagSkeleton,
  Tooltip,
} from '@carbon/react';
import {Folder} from '@carbon/icons-react';

import {t} from 'translation';
import {EntityListEntity, GenericEntity} from 'types';

import {formatRole} from './formatters';

import './CollectionHeader.scss';

interface CollectionHeaderProps {
  collection: GenericEntity | null;
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
          <Tooltip content={collectionEntity.name} position="bottom" overflowOnly>
            <span className="text">{collectionEntity.name}</span>
          </Tooltip>
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
