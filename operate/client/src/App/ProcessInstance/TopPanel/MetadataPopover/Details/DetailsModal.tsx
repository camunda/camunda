/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {JSONEditorModal} from 'modules/components/JSONEditorModal';
import {useGetIncidentsByElementInstance} from 'modules/queries/incidents/useGetIncidentsByElementInstance';
import {buildMetadata} from './buildMetadata';
import type {InstanceMetadata} from '../types';
import {resolveIncidentErrorType} from '../Incidents/resolveIncidentErrorType';

type Props = {
  elementName: string;
  elementInstanceKey: string;
  instanceMetadata: InstanceMetadata;
  isVisible: boolean;
  onClose: () => void;
};

const DetailsModal: React.FC<Props> = ({
  elementName,
  elementInstanceKey,
  instanceMetadata,
  isVisible,
  onClose,
}) => {
  const {data} = useGetIncidentsByElementInstance(elementInstanceKey, {
    enabled: isVisible && instanceMetadata.hasIncident,
  });
  const singleIncident = data?.page.totalItems === 1 ? data?.items[0] : null;
  const incident =
    singleIncident !== null
      ? {
          errorType: resolveIncidentErrorType(singleIncident.errorType),
          errorMessage: singleIncident.errorMessage,
        }
      : null;

  return (
    <JSONEditorModal
      isVisible={isVisible}
      onClose={onClose}
      title={`Element "${elementName}" ${elementInstanceKey} Metadata`}
      value={buildMetadata(instanceMetadata, incident)}
      readOnly
    />
  );
};

export {DetailsModal};
