/**
 * Type augmentation for @carbon/react to ensure MenuButton and MenuItem are properly exported
 * This works around a type resolution issue in @carbon/react 1.57.0
 */
declare module '@carbon/react' {
  export * from '@carbon/react/lib/components/Menu';
  export * from '@carbon/react/lib/components/MenuButton';
}

