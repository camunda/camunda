/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState} from 'react';
import update from 'immutability-helper';

import {Button, Icon, TextEditor} from 'components';

import TextTileEditModal from './TextTileEditModal';

import './TextTile.scss';

export default function TextTile({tile, children = () => {}, onTileUpdate}) {
  const [reloadState, setReloadState] = useState(0);
  const [isModalOpen, setIsModalOpen] = useState(false);

  const reloadTile = () => {
    setReloadState((prevReloadState) => prevReloadState + 1);
  };

  const handleEdit = () => {
    setIsModalOpen(!isModalOpen);
  };

  const onConfirm = (text) => {
    onTileUpdate(update(tile, {configuration: {text: {$set: text}}}));
  };

  if (!tile?.configuration?.text) {
    return null;
  }

  return (
    <>
      <div className="TextTile DashboardTile__wrapper">
        <TextEditor key={reloadState} initialValue={tile.configuration.text} />
        {children({loadTileData: reloadTile})}
        {children && (
          <Button className="EditButton EditTextTile" onClick={handleEdit}>
            <Icon type="edit-small" />
          </Button>
        )}
      </div>
      {isModalOpen && (
        <TextTileEditModal
          initialValue={tile.configuration.text}
          onClose={() => setIsModalOpen(false)}
          onConfirm={onConfirm}
        />
      )}
    </>
  );
}

TextTile.isTextTile = function (tile) {
  return !!tile.configuration?.text;
};
