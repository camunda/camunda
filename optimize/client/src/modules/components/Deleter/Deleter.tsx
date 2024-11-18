/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useEffect, useRef} from 'react';
import {Button} from '@carbon/react';

import {Modal, Loading} from 'components';
import {showError} from 'notifications';
import {deleteEntity as deleteEntityService} from 'services';
import {t} from 'translation';
import {useErrorHandling} from 'hooks';
import {EntityListEntity} from 'types';
import {ErrorResponse} from 'request';

const sectionOrder = ['report', 'dashboard', 'alert', 'collection'];

interface DeleterProps {
  entity: EntityListEntity;
  type: string;
  descriptionText?: string;
  deleteText?: string;
  deleteButtonText?: string;
  isReversableAction?: boolean;
  checkConflicts?: (entity: EntityListEntity) => Promise<{conflictedItems: []}>;
  onConflict?: () => void;
  onClose: () => void;
  onDelete: () => void;
  deleteEntity?: ({entityType, id}: {entityType: string; id: string}) => Promise<Response>;
  getName?: (entity: EntityListEntity) => string;
}

type Conflict = {id: string; type: string; name: string};

export default function Deleter({
  entity,
  type,
  descriptionText,
  deleteText,
  deleteButtonText,
  isReversableAction = true,
  checkConflicts,
  onConflict,
  onClose,
  onDelete,
  deleteEntity = ({entityType, id}) => deleteEntityService(entityType, id),
  getName = ({name}) => name,
}: DeleterProps) {
  const [conflicts, setConflicts] = useState<Record<string, Conflict[]>>({});
  const [loading, setLoading] = useState(false);
  const cancelButtonRef = useRef<HTMLButtonElement>(null);
  const {mightFail} = useErrorHandling();

  useEffect(() => {
    if (entity && checkConflicts) {
      setLoading(true);
      mightFail(
        checkConflicts(entity),
        (response) => {
          if (typeof response === 'boolean') {
            if (response) {
              onConflict?.();
            }
          } else {
            setConflicts(
              response.conflictedItems.reduce(
                (obj: Record<string, Conflict[]>, conflict: Conflict) => {
                  obj[conflict.type] = obj[conflict.type] || [];
                  obj[conflict.type]?.push(conflict);
                  return obj;
                },
                {}
              )
            );
          }
        },
        (error: ErrorResponse) => {
          showError(error);
          setConflicts({});
        },
        () => setLoading(false)
      );
    } else {
      setConflicts({});
      setLoading(false);
      cancelButtonRef.current?.focus();
    }
  }, [entity, checkConflicts, mightFail, onConflict]);

  useEffect(() => {
    if (!loading) {
      cancelButtonRef.current?.focus();
    }
  }, [loading]);

  const handleDelete = () => {
    if (!entity) {
      return;
    }

    setLoading(true);
    mightFail(
      deleteEntity(entity),
      () => {
        onDelete();
        handleClose();
      },
      showError,
      () => setLoading(false)
    );
  };

  const handleClose = () => {
    setConflicts({});
    setLoading(false);
    onClose();
  };

  if (!entity) {
    return null;
  }

  const translatedType = t(`common.deleter.types.${type}`).toString();

  return (
    <Modal open onClose={handleClose} className="Deleter">
      <Modal.Header title={deleteText || t('common.deleteEntity', {entity: translatedType})} />
      <Modal.Content>
        {loading ? (
          <Loading />
        ) : (
          <>
            <p>
              {descriptionText ||
                t('common.deleter.permanent', {
                  name: getName(entity),
                  type: translatedType,
                })}
            </p>
            {Object.keys(conflicts)
              .sort((a, b) => sectionOrder.indexOf(a) - sectionOrder.indexOf(b))
              .map((conflictType) => (
                <div key={conflictType}>
                  {t(`common.deleter.affectedMessage.${type}.${conflictType}`)}
                  <ul>
                    {conflicts[conflictType]?.map(({id, name}) => <li key={id}>{name || id}</li>)}
                  </ul>
                </div>
              ))}
            <p>{!isReversableAction && <b>{t('common.deleter.noUndo')}</b>}</p>
          </>
        )}
      </Modal.Content>
      <Modal.Footer>
        <Button
          disabled={loading}
          className="close"
          onClick={handleClose}
          ref={cancelButtonRef}
          kind="secondary"
        >
          {t('common.cancel')}
        </Button>
        <Button kind="danger" disabled={loading} className="confirm" onClick={handleDelete}>
          {deleteButtonText || deleteText || t('common.deleteEntity', {entity: translatedType})}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
