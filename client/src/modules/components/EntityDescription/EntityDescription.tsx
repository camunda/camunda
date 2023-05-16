/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useRef, useState} from 'react';
import {Button} from '@carbon/react';
import classnames from 'classnames';

import {Button as LegacyButton, Icon, Modal, TextEditor} from 'components';
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

  useEffect(() => {
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
  }, []);

  return (
    <>
      <div className="EntityDescription">
        <p
          ref={descriptionRef}
          className={classnames('description', {
            overflowHidden: !isDescriptionOpen,
            empty: !editedDescription,
          })}
        >
          {description || t('common.noDescription')}
        </p>
        {!onEdit && showToggleButton && (
          <span className="toggle" onClick={toggleDescription}>
            {isDescriptionOpen ? t('common.less') : t('common.more')}
          </span>
        )}
        {onEdit && (
          <LegacyButton className="edit" link onClick={openModal}>
            <Icon size={12} type={editedDescription ? 'edit' : 'plus'} />
            {t(`common.${editedDescription ? 'edit' : 'add'}`)}
          </LegacyButton>
        )}
      </div>
      <Modal className="EntityDescriptionEditModal" open={isEditModalOpen} onClose={handleCancel}>
        <Modal.Header>
          {t(`common.${description ? 'editName' : 'addName'}`, {
            name: t('common.description'),
          })}
        </Modal.Header>
        <Modal.Content>
          <TextEditor
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
