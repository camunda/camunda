/*
 * Shim module that re-exports the real component from @camunda/camunda-composite-components
 * so existing local imports keep working.
 */
export {C3AdvancedSearchFilters} from '@camunda/camunda-composite-components';
export type {
  C3AdvancedSearchFiltersProps,
  FieldSpecMap,
  FieldSpec,
  FilterPayload,
  AdvancedFilterOperator,
} from '@camunda/camunda-composite-components';
