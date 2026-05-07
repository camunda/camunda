/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {VariablesContent} from './styled';
import {observer} from 'mobx-react';
import {reaction} from 'mobx';
import {useForm, useFormState} from 'react-final-form';
import {modificationsStore} from 'modules/stores/modifications';
import {useFieldArray} from 'react-final-form-arrays';
import {type VariableFormValues} from 'modules/types/variables';
import {VariablesTable} from './VariablesTable';
import {Footer} from './Footer';
import {Skeleton} from './Skeleton';
import {useNewScopeKeyForElement} from 'modules/hooks/modifications';
import {useIsProcessInstanceRunning} from 'modules/queries/processInstance/useIsProcessInstanceRunning';
import {useVariables} from 'modules/queries/variables/useVariables';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {useVariableScopeKey} from 'modules/hooks/variables';
import {
  VariablesToolbar,
  type VariableTypeFilter,
} from './documents/VariablesToolbar';

type Props = {
  isVariableModificationAllowed?: boolean;
};

type FooterVariant = React.ComponentProps<typeof Footer>['variant'];

const Variables: React.FC<Props> = observer(
  ({isVariableModificationAllowed = false}) => {
    const {displayStatus} = useVariables();
    const {selectedElementId, resolvedElementInstance, hasSelection} =
      useProcessInstanceElementSelection();
    const newScopeKeyForElement = useNewScopeKeyForElement(selectedElementId);
    const {data: isProcessInstanceRunning} = useIsProcessInstanceRunning();
    const [footerVariant, setFooterVariant] =
      useState<FooterVariant>('initial');
    const [searchTerm, setSearchTerm] = useState('');
    const [typeFilter, setTypeFilter] = useState<VariableTypeFilter>('all');

    const scopeKey = useVariableScopeKey(newScopeKeyForElement);

    const {isModificationModeEnabled} = modificationsStore;

    const form = useForm<VariableFormValues>();

    useEffect(() => {
      const disposer = reaction(
        () => modificationsStore.isModificationModeEnabled,
        (isModificationModeEnabled) => {
          if (!isModificationModeEnabled) {
            form.reset({});
          }
        },
      );

      return disposer;
    }, [isModificationModeEnabled, form]);

    const {initialValues} = useFormState<VariableFormValues>();

    const fieldArray = useFieldArray('newVariables');

    const isViewMode = isModificationModeEnabled
      ? fieldArray.fields.length === 0 &&
        modificationsStore.getAddVariableModifications(scopeKey).length === 0
      : initialValues === undefined ||
        Object.values(initialValues).length === 0;

    useEffect(() => {
      const isSelectedElementInstanceRunning =
        resolvedElementInstance?.state === 'ACTIVE';

      if (!isProcessInstanceRunning) {
        setFooterVariant('disabled');
        return;
      }

      if (initialValues?.name === '' && initialValues?.value === '') {
        setFooterVariant('add-variable');
        return;
      }

      if (!isViewMode || (hasSelection && !isSelectedElementInstanceRunning)) {
        setFooterVariant('disabled');
        return;
      }

      setFooterVariant('initial');
    }, [
      isProcessInstanceRunning,
      initialValues,
      isViewMode,
      hasSelection,
      resolvedElementInstance?.state,
    ]);

    if (displayStatus === 'no-content') {
      return null;
    }

    // Prototype: mock document variables are always injected client-side, so the
    // table renders rows even when the backend returns nothing. The skeleton
    // path still wins so we don't flash content during the initial fetch.
    const showTable = !isViewMode || displayStatus !== 'skeleton';

    return (
      <VariablesContent>
        {!isModificationModeEnabled && showTable && (
          <VariablesToolbar
            searchTerm={searchTerm}
            onSearchChange={setSearchTerm}
            typeFilter={typeFilter}
            onTypeFilterChange={setTypeFilter}
          />
        )}
        {isViewMode && displayStatus === 'skeleton' && <Skeleton />}
        {showTable && (
          <VariablesTable
            scopeId={scopeKey}
            isModificationModeEnabled={isModificationModeEnabled}
            isVariableModificationAllowed={isVariableModificationAllowed}
            searchTerm={searchTerm}
            typeFilter={typeFilter}
          />
        )}

        {!isModificationModeEnabled && <Footer variant={footerVariant} />}
      </VariablesContent>
    );
  },
);

export {Variables};
