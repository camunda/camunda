/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

type ContainerProps = {
  highlightableRows: number[];
};

const Container = styled.div<ContainerProps>`
  ${({theme, highlightableRows}) => {
    const colors = theme.colors.decisionPanel;

    return css`
      background: ${colors.background};

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
        }

        .literal-expression-properties {
          border-color: ${colors.border};
          border-width: 1px 2px 2px 2px;
        }
      }
    `;
  }}
  overflow: auto;
  height: 100%;
`;

const Decision = styled.div`
  padding: 30px 20px;
`;

const IncidentBanner = styled.div`
  ${({theme}) => {
    return css`
      background-color: ${theme.colors.incidentsAndErrors};
      color: ${theme.colors.white};
      font-size: 15px;
      font-weight: 500;
      height: 42px;
      display: flex;
      align-items: center;
      justify-content: center;
    `;
  }}
`;
export {Container, Decision, IncidentBanner};
