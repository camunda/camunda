/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

type ContainerProps = {
  highlightableRows: number[];
};

const Container = styled.div<ContainerProps>`
  ${({highlightableRows}) => {
    return css`
      width: 100%;
      padding: var(--cds-spacing-07) var(--cds-spacing-06);

      &,
      & > ${ViewerCanvas} {
        height: 100%;
        min-height: 200px;
      }

      .powered-by {
        display: none;
      }

      .dmn-decision-table-container {
        --decision-table-font-family: inherit;
        --table-head-clause-color: var(--cds-text-primary);
        --table-head-variable-color: var(--cds-text-primary);
        --table-head-separator-color: var(--cds-border-subtle);
        --table-color: var(--cds-text-primary);
        --table-cell-color: var(--cds-text-primary);
        --decision-table-color: var(--cds-text-primary);
        --decision-table-properties-color: var(--cds-text-primary);
        --table-head-border-color: var(--cds-icon-secondary);
        --table-cell-border-color: var(--cds-icon-secondary);
        --decision-table-background-color: var(--cds-layer);
        --table-row-alternative-background-color: var(--cds-layer);

        .decision-table-properties {
          border-width: 2px 2px 1px 2px;
        }

        .tjs-table-container {
          background-color: var(--cds-layer);
          border-width: 2px 2px 1px 2px;

          tbody {
            ${highlightableRows.map((rowIndex) => {
              return css`
                tr:nth-child(${rowIndex}) {
                  background-color: var(--cds-highlight);
                  td {
                    color: var(--cds-text-primary);
                    background-color: var(--cds-highlight);
                  }
                }
              `;
            })}
          }
        }
      }

      .dmn-literal-expression-container {
        --literal-expression-font-family: inherit;
        --literal-expression-font-family-monospace: var(
          --cds-code-01-font-family
        );
        --decision-properties-border-color: var(--cds-icon-secondary);
        --decision-properties-color: var(--cds-text-primary);
        --textarea-color: var(--cds-text-primary);
        --literal-expression-properties-color: var(--cds-text-primary);

        .decision-properties {
          background-color: var(--cds-layer);
          border-color: var(--cds-icon-secondary);
          border-width: 2px 2px 1px 2px;
        }

        .textarea {
          background-color: var(--cds-layer);
          border-color: var(--cds-icon-secondary);
          border-width: 1px 2px;
          height: auto;
        }

        .literal-expression-properties {
          background-color: var(--cds-layer);
          border-color: var(--cds-icon-secondary);
          border-width: 1px 2px 2px 2px;
        }

        table {
          border-collapse: unset;
          padding: unset;
        }
      }
    `;
  }}
`;

const ViewerCanvas = styled.div``;

export {Container, ViewerCanvas};
