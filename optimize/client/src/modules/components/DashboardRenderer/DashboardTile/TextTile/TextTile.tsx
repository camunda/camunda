/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import update from 'immutability-helper';
import {SerializedEditorState} from 'lexical';

import {TextEditor} from 'components';
import {DashboardTile, TextTile as TTextTile} from 'types';
import {t} from 'translation';

import {DashboardTileProps} from '../types';

import TextTileEditModal from './TextTileEditModal';

import './TextTile.scss';

export default function TextTile({tile, children, onTileUpdate}: DashboardTileProps) {
  const [reloadState, setReloadState] = useState(0);
  const [isModalOpen, setIsModalOpen] = useState(false);

  const reloadTile = () => {
    setReloadState((prevReloadState) => prevReloadState + 1);
  };

  const openEditModal = () => {
    setIsModalOpen(!isModalOpen);
  };

  const handleTileUpdate = (text: SerializedEditorState | null) => {
    onTileUpdate(update(tile, {configuration: {text: {$set: text}}}));
  };

  if (!TextTile.isTileOfType(tile)) {
    return null;
  }

  return (
    <>
      <div className="TextTile DashboardTile">
        <TextEditor
          label={t('report.textTile').toString()}
          hideLabel
          key={reloadState}
          initialValue={tile.configuration.text}
        />
        {children?.({loadTileData: reloadTile, onTileUpdate: openEditModal})}
      </div>
      {isModalOpen && (
        <TextTileEditModal
          initialValue={tile.configuration.text}
          onClose={() => setIsModalOpen(false)}
          onConfirm={handleTileUpdate}
        />
      )}
    </>
  );
}

TextTile.isTileOfType = function (tile: Pick<DashboardTile, 'configuration'>): tile is TTextTile {
  return !!tile.configuration && 'text' in tile.configuration;
};
