/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import type {StateProps} from 'modules/components/ModalStateManager';
import {useVariable} from 'modules/queries/variables/useVariable';
import {parseDocumentVariable} from '../parseDocumentVariable';
import type {DocumentInfo} from 'App/ProcessInstance/DocumentsView/documentInfo';
import {DocumentListModal} from 'App/ProcessInstance/DocumentsView/DocumentListModal';

const LOADING_HINT =
  'Loading the full variable value... More documents may exist for this variable.';
const ERROR_HINT =
  'Failed to load the full variable value. More documents may exist for this variable.';

type Props = {
  documents: DocumentInfo[];
  isLowerBound: boolean;
  variableKey: string;
  variableName: string;
};

const VariableDocumentListModal: React.FC<StateProps & Props> = ({
  open,
  setOpen,
  documents,
  isLowerBound,
  variableKey,
  variableName,
}) => {
  const {data, isError, isLoading} = useVariable(variableKey, {
    enabled: open && isLowerBound,
  });

  const resolvedDocuments = useMemo(() => {
    if (data?.value === undefined) {
      return documents;
    }

    const result = parseDocumentVariable(data?.value, false);
    if (result === null) {
      return documents;
    }

    return result.type === 'list' ? result.documents : [result.document];
  }, [data?.value, documents]);

  return (
    <DocumentListModal
      open={open}
      setOpen={setOpen}
      documents={resolvedDocuments}
      labelSuffix={variableName}
      loadingHint={isLoading ? LOADING_HINT : undefined}
      errorHint={isError ? ERROR_HINT : undefined}
    />
  );
};

export {VariableDocumentListModal};
