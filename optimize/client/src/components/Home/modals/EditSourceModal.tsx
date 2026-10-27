/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useState} from 'react';
import {Button} from '@carbon/react';

import {Modal, Checklist} from 'components';
import {t} from 'translation';
import {Tenant} from 'types';
import {useErrorHandling} from 'hooks';
import {UNAUTHORIZED_TENANT_ID} from 'services';

import {getDefinitionTenants, formatTenants} from './service';

interface Source {
  tenants: Tenant[];
  definitionKey: string;
  definitionType: string;
  definitionName: string;
}

interface EditSourceModalProps {
  source: Source;
  onConfirm: (ids: (string | null)[]) => void;
  onClose: () => void;
}

export default function EditSourceModal({source, onConfirm, onClose}: EditSourceModalProps) {
  const [selectedTenants, setSelectedTenants] = useState(source.tenants);
  const [definitionTenants, setDefinitionTenants] = useState<Tenant[]>();
  const {mightFail} = useErrorHandling();

  const getUnauthorizedTenants = useCallback(
    (tenants: Tenant[]) => tenants.filter((tenant) => tenant.id === UNAUTHORIZED_TENANT_ID),
    []
  );

  useEffect(() => {
    const {definitionKey, definitionType} = source;
    mightFail(getDefinitionTenants(definitionKey, definitionType), ({tenants}) => {
      setDefinitionTenants([...tenants, ...getUnauthorizedTenants(source.tenants)]);
    });
  }, [source, mightFail, getUnauthorizedTenants]);

  const onConfirmHandler = () => {
    if (selectedTenants.length > 0) {
      onConfirm(selectedTenants.map(({id}) => id));
    }
  };

  const updateSelectedTenants = (newSelectedTenants: (Tenant | undefined)[]) => {
    if (!newSelectedTenants.length) {
      setSelectedTenants((selectedTenants) => getUnauthorizedTenants(selectedTenants));
    } else {
      setSelectedTenants(
        newSelectedTenants.filter((tenant): tenant is Tenant => typeof tenant !== 'undefined')
      );
    }
  };

  const modalTitle = `${t('common.editTenants')} - ${source.definitionName || source.definitionKey}`;

  return (
    <Modal className="EditSourceModal" open onClose={onClose}>
      <Modal.Header title={modalTitle} />
      <Modal.Content>
        <Checklist
          columnLabel={t('common.tenant.label')}
          selectedItems={selectedTenants}
          allItems={definitionTenants ?? []}
          onChange={updateSelectedTenants}
          formatter={formatTenants}
          loading={!definitionTenants}
        />
      </Modal.Content>
      <Modal.Footer>
        <Button kind="secondary" className="cancel" onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Button className="confirm" disabled={!selectedTenants.length} onClick={onConfirmHandler}>
          {t('common.apply')}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
