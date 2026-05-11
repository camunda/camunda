/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ViewFullVariableButton} from './ViewFullVariableButton';
import {InlineJsonEditor} from 'modules/components/InlineJsonEditor';
import {useVariable} from 'modules/queries/variables/useVariable';
import {recognizeDocumentValue} from './documents/recognize';
import {DocumentValueCell} from './documents/DocumentValueCell';

type Props = {
  variableKey: string;
  variableName: string;
  value: string;
  isTruncated: boolean | null;
  isModificationModeEnabled: boolean | undefined;
  isProcessInstanceRunning: boolean | undefined;
};

const VariableValueCell: React.FC<Props> = ({
  variableKey,
  variableName,
  value,
  isTruncated,
  isModificationModeEnabled,
  isProcessInstanceRunning,
}) => {
  const {refetch} = useVariable(variableKey, {enabled: false});

  // Per-variable document detection via the camunda.document.type discriminator.
  // For truncated values we fall back to a string-pattern match so an array of
  // document references can still surface a lower-bound count.
  const recognition = recognizeDocumentValue(value, Boolean(isTruncated));

  if (recognition.kind !== 'none') {
    return <DocumentValueCell recognition={recognition} />;
  }

  return (
    <InlineJsonEditor
      value={value}
      label={variableName}
      isTruncatedValue={Boolean(isTruncated)}
      readOnly
      onCopy={
        isTruncated
          ? async () => {
              const result = await refetch();
              if (result.data) {
                return result.data.value;
              }
              throw (
                result.error ??
                new Error(`Failed to fetch variable: ${variableName}`)
              );
            }
          : undefined
      }
      renderButton={
        isTruncated
          ? () => (
              <ViewFullVariableButton
                mode="show"
                variableKey={variableKey}
                variableName={variableName}
                variableValue={value}
                buttonLabel="Show all"
                canEdit={
                  !isModificationModeEnabled && !!isProcessInstanceRunning
                }
              />
            )
          : undefined
      }
    />
  );
};

export {VariableValueCell};
