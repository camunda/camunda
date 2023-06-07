/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {createGlobalStyle, css} from 'styled-components';

const MENU_OPTIONS_STYLES_CLASSNAME =
  'cds--custom-sorting-menu-options-wrapper';

const MenuOptionsStyles = createGlobalStyle`
  ${() => css`
    .${MENU_OPTIONS_STYLES_CLASSNAME} {
      width: 180px;
    }
  `}
`;

const Container = styled.section`
  width: 100%;
  padding: var(--cds-spacing-04) var(--cds-spacing-05);
  border-bottom: 1px solid var(--cds-border-subtle);
`;

const FormElement = styled.form`
  display: grid;
  align-items: flex-end;
  grid-template-columns: 1fr min-content;
  gap: var(--cds-spacing-03);
  width: 100%;
`;

const SortItemContainer = styled.div`
  width: 100%;
  display: flex;
  gap: var(--cds-spacing-03);
`;

const MenuItemWrapper = styled.span`
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

export {
  Container,
  FormElement,
  SortItemContainer,
  MenuItemWrapper,
  MenuOptionsStyles,
  MENU_OPTIONS_STYLES_CLASSNAME,
};
