/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useState, useEffect} from 'react';
import {observer} from 'mobx-react';
import {Modal, TextArea} from '@carbon/react';
import {type AuditLogEntry} from '../mocks';
import {ModalContent} from './styled';

interface CommentModalProps {
  isOpen: boolean;
  onClose: () => void;
  auditLogEntry: AuditLogEntry | null;
  onSaveComment: (entryId: string, newComment: string) => void;
}

const CommentModal: React.FC<CommentModalProps> = observer(
  ({isOpen, onClose, auditLogEntry, onSaveComment}) => {
    const [comment, setComment] = useState('');

    useEffect(() => {
      if (auditLogEntry) {
        setComment(auditLogEntry.comment || '');
      }
    }, [auditLogEntry]);

    if (!auditLogEntry) return null;

    const handleSave = () => {
      onSaveComment(auditLogEntry.id, comment);
      onClose();
    };

    return (
      <Modal
        open={isOpen}
        onRequestClose={onClose}
        modalHeading="Comment"
        primaryButtonText="Save"
        secondaryButtonText="Cancel"
        onRequestSubmit={handleSave}
        onSecondarySubmit={onClose}
        size="sm"
      >
        <ModalContent>
          <TextArea
            id="comment-textarea"
            labelText="Comment"
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            placeholder="Add a comment..."
            rows={4}
          />
        </ModalContent>
      </Modal>
    );
  },
);

export {CommentModal};
