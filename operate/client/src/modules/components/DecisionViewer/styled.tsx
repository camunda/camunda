/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import styled, {css} from 'styled-components';

type ContainerProps = {
  $highlightableRows: number[];
};

const Container = styled.div<ContainerProps>`
  ${({$highlightableRows}) => {
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
            ${$highlightableRows.map((rowIndex) => {
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
          min-height: 0;
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
