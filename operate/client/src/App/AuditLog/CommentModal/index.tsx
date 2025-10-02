/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Modal, TextArea} from '@carbon/react';
import {useUpsertComment} from 'modules/mutations/auditLog/useUpsertComment';
import {createPortal} from 'react-dom';

type CommentModalProps = {
  open: boolean;
  onClose: () => void;
  entryId: string;
  initialComment?: string;
  mode: 'view' | 'edit' | 'add';
};

const CommentModal: React.FC<CommentModalProps> = ({
  open,
  onClose,
  entryId,
  initialComment = '',
  mode,
}) => {
  const [comment, setComment] = useState(initialComment);
  const {mutate: upsertComment, isPending} = useUpsertComment();

  const isReadOnly = mode === 'view';
  const title =
    mode === 'view'
      ? 'View Comment'
      : mode === 'edit'
        ? 'Edit Comment'
        : 'Add Comment';

  const handleSubmit = () => {
    if (comment.trim()) {
      upsertComment(
        {id: entryId, comment: comment.trim()},
        {
          onSuccess: () => {
            onClose();
          },
        },
      );
    }
  };

  return createPortal(
    <Modal
      open={open}
      onRequestClose={onClose}
      modalHeading={title}
      primaryButtonText={isReadOnly ? undefined : 'Save'}
      secondaryButtonText={isReadOnly ? 'Close' : 'Cancel'}
      onRequestSubmit={isReadOnly ? undefined : handleSubmit}
      primaryButtonDisabled={isPending || !comment.trim()}
      size="md"
    >
      <TextArea
        labelText="Comment"
        value={comment}
        onChange={(e) => setComment(e.target.value)}
        rows={6}
        disabled={isReadOnly}
        placeholder={isReadOnly ? 'No comment' : 'Enter your comment here...'}
      />
    </Modal>,
    document.body,
  );
};

export {CommentModal};
