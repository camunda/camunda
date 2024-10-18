/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef, useState} from 'react';
import {Button} from '@carbon/react';
import classnames from 'classnames';
import {Edit, Add} from '@carbon/icons-react';

import {Modal, TextEditor} from 'components';
import {t} from 'translation';

import './EntityDescription.scss';

const DESCRIPTION_MAX_CHARACTERS = 400;

type EntityDescriptionProps = {
  description: string | null;
} & (
  | {
      onEdit: (text: string | null) => void;
    }
  | {onEdit?: undefined}
);

export default function EntityDescription({description, onEdit}: EntityDescriptionProps) {
  const [isDescriptionOpen, setIsDescriptionOpen] = useState(false);
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [editedDescription, setEditedDescription] = useState(description);
  const [showToggleButton, setShowToggleButton] = useState(false);

  const descriptionRef = useRef<HTMLParagraphElement>(null);

  const toggleDescription = () => {
    setIsDescriptionOpen(!isDescriptionOpen);
  };

  const closeModal = () => {
    setIsEditModalOpen(false);
  };

  const openModal = () => {
    setIsEditModalOpen(true);
  };

  const handleDescriptionChange = (text: string) => setEditedDescription(text);

  const handleConfirm = () => {
    onEdit?.(editedDescription || null);
    closeModal();
  };

  const handleCancel = () => {
    setEditedDescription(description);
    closeModal();
  };

  const isTextTooLong = (editedDescription?.length || 0) > DESCRIPTION_MAX_CHARACTERS;

  const calculateShowLessButton = () => {
    setShowToggleButton(false);
    if (descriptionRef.current) {
      const CONTAINER_RIGHT_MARGIN = 48;
      const TOGGLE_BUTTON_TRESHOLD = 10;

      const {width: containerWidth = 0} =
        descriptionRef.current.parentElement?.getBoundingClientRect() || {};
      const {width: buttonWidth = 0} =
        descriptionRef.current.nextElementSibling?.getBoundingClientRect() || {};
      const {width: descriptionWidth} = descriptionRef.current.getBoundingClientRect();

      if (
        containerWidth - descriptionWidth - buttonWidth - CONTAINER_RIGHT_MARGIN <
        TOGGLE_BUTTON_TRESHOLD
      ) {
        setShowToggleButton(true);
      }
    }
  };

  useEffect(() => {
    // This is needed to get the new description field size after update
    setTimeout(() => {
      calculateShowLessButton();
    });
    window.addEventListener('resize', calculateShowLessButton, false);

    return () => window.removeEventListener('resize', calculateShowLessButton);
  }, []);

  return (
    <>
      <div className="EntityDescription">
        {description && (
          <p
            ref={descriptionRef}
            className={classnames('description', {
              overflowHidden: !isDescriptionOpen,
            })}
          >
            {description}
          </p>
        )}
        {!onEdit && showToggleButton && (
          <Button kind="ghost" size="sm" onClick={toggleDescription} className="toggle">
            {isDescriptionOpen ? t('common.less') : t('common.more')}
          </Button>
        )}
        {onEdit && description && (
          <Button
            kind="ghost"
            size="sm"
            className="edit"
            onClick={openModal}
            renderIcon={Edit}
            hasIconOnly
            iconDescription={t('common.edit').toString()}
          />
        )}
        {onEdit && !description && (
          <Button kind="tertiary" size="sm" className="add" onClick={openModal} renderIcon={Add}>
            {t('report.addDescription')}
          </Button>
        )}
      </div>
      <Modal className="EntityDescriptionEditModal" open={isEditModalOpen} onClose={handleCancel}>
        <Modal.Header
          title={t(`common.${description ? 'editName' : 'addName'}`, {
            name: t('common.description').toString(),
          })}
        />
        <Modal.Content>
          <TextEditor
            label={t('common.description').toString()}
            hideLabel
            simpleEditor
            initialValue={editedDescription}
            onChange={handleDescriptionChange}
            limit={DESCRIPTION_MAX_CHARACTERS}
          />
          <TextEditor.CharCount
            editorState={editedDescription}
            limit={DESCRIPTION_MAX_CHARACTERS}
          />
        </Modal.Content>
        <Modal.Footer>
          <Button kind="secondary" className="cancel" onClick={handleCancel}>
            {t('common.cancel')}
          </Button>
          <Button className="confirm" onClick={handleConfirm} disabled={isTextTooLong}>
            {t('common.save')}
          </Button>
        </Modal.Footer>
      </Modal>
    </>
  );
}
