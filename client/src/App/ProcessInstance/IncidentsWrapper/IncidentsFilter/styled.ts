/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import Dropdown from 'modules/components/Dropdown';
import {Button} from 'modules/components/Button';

const FiltersWrapper = styled.div`
  ${({theme}) => {
    const colors = theme.colors.incidentsFilter.filtersWrapper;

    return css`
      padding: 18px 20px 19px;
      background-color: ${colors.backgroundColor};
      border-bottom: 1px solid ${theme.colors.borderColor};
    `;
  }}
`;

const Content = styled.div`
  position: relative;
  display: flex;
`;

const FilterRow = styled.div`
  display: flex;
  &:first-child {
    margin-bottom: 12px;
  }
`;

const Label = styled.span`
  ${({theme}) => {
    const colors = theme.colors.incidentsFilter.label;

    return css`
      color: ${theme.colors.text02};
      position: relative;
      width: 120px;
      padding-right: 17px;
      margin-right: 18px;
      font-size: 15px;
      font-weight: bold;
      text-align: right;
      opacity: 0.9;

      &:after {
        content: '';
        position: absolute;
        top: calc(50% - 8px);
        right: 0;
        width: 1px;
        height: 16px;
        background-color: ${colors.backgroundColor};
      }
    `;
  }}
`;

const Ul = styled.ul`
  margin: 0;
  padding: 0;
  li {
    display: inline-block;
    margin-right: 10px;
    margin-bottom: 5px;
  }
`;

const MoreDropdown = styled(Dropdown)`
  ${({theme}) => {
    const colors = theme.colors.incidentsFilter.moreDropdown;

    return css`
      [data-testid='dropdown-toggle'] {
        display: flex;
        align-items: center;
        border-radius: 16px;
        font-size: 13px;
        padding: 3px 10px;
        background-color: ${theme.colors.ui05};
        border: 1px solid ${colors.dropdownToggle.borderColor};
        color: 1px solid ${colors.dropdownToggle.color};

        &:active,
        &[data-button-open='true'] {
          color: ${theme.colors.white};
          background: ${theme.colors.selections};
          border-color: ${theme.colors.primaryButton03};
        }
      }

      [data-testid='menu'] {
        margin-top: 9px;
        width: 235px;
        padding: 0 6px;
        z-index: 4;
      }

      li {
        width: 100%;

        &:not(:last-child) {
          border-bottom: 0;
        }
        /* style arrows */
        &:first-child {
          &:before {
            margin-right: -4px;
          }

          &:after {
            margin-right: -3px;
          }

          &:hover {
            &:after {
              border-bottom-color: ${colors.item.borderColor};
            }
          }
        }
        /* end style arrows */
      }

      button {
        border-radius: 16px !important;
      }
    `;
  }}
`;

const ButtonWrapper = styled.div`
  ${({theme}) => {
    const colors = theme.colors.incidentsFilter.buttonsWrapper;

    return css`
      position: relative;
      right: -20px;
      width: 141px;
      display: flex;
      justify-content: center;
      align-items: center;

      &:before {
        content: '';
        position: absolute;
        top: calc(50% - 33px);
        left: 0;
        width: 1px;
        height: 71px;
        background-color: ${colors.backgroundColor};
      }
    `;
  }}
`;

const PillsWrapper = styled.div`
  flex-grow: 1;
`;

const ClearButton = styled(Button)`
  font-weight: bold;
  font-size: 13px;
`;

export {
  FiltersWrapper,
  Content,
  FilterRow,
  Label,
  Ul,
  MoreDropdown,
  ButtonWrapper,
  PillsWrapper,
  ClearButton,
};
