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
  ${({theme, highlightableRows}) => {
    const colors = theme.colors.decisionViewer;

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
        --table-head-clause-color: ${theme.colors.text01};
        --table-head-variable-color: ${colors.text};
        --table-color: ${colors.text};
        --table-cell-color: ${colors.text};
        --decision-table-color: ${colors.text};
        --decision-table-properties-color: ${colors.text};
        --table-head-border-color: ${colors.border};
        --table-cell-border-color: ${colors.border};
        --decision-table-background-color: ${colors.background};
        --table-row-alternative-background-color: ${colors.background};

        .decision-table-properties {
          border-width: 2px 2px 1px 2px;
        }

        .tjs-table-container {
          border-width: 2px 2px 1px 2px;

          tbody {
            ${highlightableRows.map((rowIndex) => {
              return css`
                tr:nth-child(${rowIndex}) {
                  background-color: ${colors.highlightedRow.background};
                  td {
                    color: ${colors.highlightedRow.color};
                    background-color: ${colors.highlightedRow.background};
                  }
                }
              `;
            })}
          }
        }
      }

      .dmn-literal-expression-container {
        --decision-properties-background-color: ${colors.background};
        --decision-properties-border-color: ${colors.border};
        --decision-properties-color: ${colors.text};
        --textarea-color: ${colors.text};
        --literal-expression-properties-color: ${colors.text};

        .decision-properties {
          border-color: ${colors.border};
          border-width: 2px 2px 1px 2px;
        }

        .textarea {
          border-color: ${colors.border};
          border-width: 1px 2px;
          height: auto;
        }

        .literal-expression-properties {
          border-color: ${colors.border};
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
