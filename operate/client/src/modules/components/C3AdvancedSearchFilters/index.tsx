/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {Stack} from '@carbon/react';

type Props = {
  // Callback when the user applies filters in the advanced search
  onApply?: () => void;
  // Callback when the user resets/clears filters in the advanced search
  onReset?: () => void;
};

// Placeholder shim for C3AdvancedSearchFilters.
// Replace this component's internals with the real implementation from camunda-cloud-management-apps PR #5035.
const C3AdvancedSearchFilters: React.FC<Props> = ({onApply, onReset}) => {
  return (
    <Stack gap={5} data-testid="c3-advanced-search-filters-placeholder">
      <div style={{fontWeight: 600}}>C3 Advanced Search Filters</div>
      <div>
        This is a placeholder component. Integrate the real C3AdvancedSearchFilters
        implementation here to enable the full feature set.
      </div>
      <div style={{fontSize: '0.875rem', color: 'var(--cds-text-secondary)'}}>
        • onApply and onReset callbacks are provided and will be wired to the modal actions.
      </div>
    </Stack>
  );
};

export {C3AdvancedSearchFilters};
