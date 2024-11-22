/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MouseEvent, useState} from 'react';
import {Button, TextInput, Form, Checkbox, Stack} from '@carbon/react';

import {Modal} from 'components';
import {t} from 'translation';
import {EntityListEntity} from 'types';

import MoveCopy, {CollectionsHome} from './MoveCopy';

interface CopyModaProps {
  onConfirm: (name: string, shouldMove: boolean, value?: string | boolean | null) => void;
  entity: EntityListEntity;
  jumpToEntity?: boolean;
  onClose: (event: MouseEvent) => boolean | void;
  collection: string;
}

export default function CopyModal({
  onConfirm,
  entity,
  jumpToEntity,
  onClose,
  collection: parentCollection,
}: CopyModaProps) {
  const [name, setName] = useState(`${entity.name} (${t('common.copyLabel')})`);
  const [moving, setMoving] = useState(false);
  const [collection, setCollection] = useState<EntityListEntity | CollectionsHome | null>(null);
  const [gotoNew, setGotoNew] = useState(true);

  const handleConfirm = () => {
    if (name && (!moving || collection)) {
      if (isCollection() && jumpToEntity) {
        onConfirm(name, gotoNew);
      } else {
        onConfirm(name, moving && gotoNew, moving && collection?.id);
      }
    }
  };

  const isCollection = () => entity.entityType === 'collection';

  return (
    <Modal className="CopyModal" open onClose={onClose}>
      <Modal.Header title={t('common.copyName', {name: entity.name || ''})} />
      <Modal.Content>
        <Form>
          <Stack gap={6}>
            <TextInput
              id="entityCopyName"
              labelText={t('home.copy.inputLabel')}
              value={name}
              autoComplete="off"
              onChange={({target: {value}}) => setName(value)}
              helperText={isCollection() && t('home.copy.copyCollectionInfo')}
            />
            {!isCollection() && (
              <MoveCopy
                entity={entity}
                parentCollection={parentCollection}
                moving={moving}
                setMoving={(moving) => setMoving(moving)}
                setCollection={(collection) => setCollection(collection)}
              />
            )}
            {jumpToEntity && (isCollection() || moving) && (
              <Checkbox
                id="gotoNew"
                labelText={t('home.copy.gotoNew')}
                checked={gotoNew}
                onChange={({target: {checked}}) => setGotoNew(checked)}
              />
            )}
          </Stack>
        </Form>
      </Modal.Content>
      <Modal.Footer>
        <Button kind="secondary" className="cancel" onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Button
          disabled={!name || (moving && !collection)}
          className="confirm"
          onClick={handleConfirm}
        >
          {t('common.copy')}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
