/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {BaseSyntheticEvent, Children, cloneElement, ComponentProps, ReactNode} from 'react';
import {MenuItemSelectable} from '@carbon/react';
import {MenuDropdown} from '@camunda/camunda-optimize-composite-components';

import {ignoreFragments, isReactElement} from 'services';
import classnames from 'classnames';
import {t} from 'translation';

export interface CarbonSelectProps<T extends object | string | number = string>
  extends Omit<ComponentProps<typeof MenuDropdown>, 'label' | 'onChange'> {
  id: string;
  value?: T;
  onChange?: (value: T) => void;
  labelText?: string | JSX.Element[];
}

export default function CarbonSelect<T extends object | string | number>(
  props: CarbonSelectProps<T>
) {
  const {labelText, ...rest} = props;
  const renderChildrenWithProps = (children: ReactNode) => {
    return Children.toArray(children)
      .filter(isReactElement)
      .map((child) => {
        const newProps: SubmenuProps | OptionProps = {
          ...child.props,
          label: child.props.label?.toString() || '',
        };

        if (child.type === CarbonSelect.Submenu) {
          newProps.selected = Children.toArray(child.props.children).some(
            (child) => isReactElement(child) && child.props.value === props.value
          );

          newProps.children = renderChildrenWithProps(child.props.children);
        } else if (child.type === CarbonSelect.Option) {
          newProps.onChange = onChange;
          newProps.selected = child.props.value === props.value;
        } else {
          console.error('Select `children` should be either an `Submenu` or `Option` component.');
          return null;
        }

        return child && cloneElement(child, newProps);
      });
  };

  const getLabel = (children = props.children) => {
    let label;

    Children.forEach(ignoreFragments(children), (child) => {
      if (child.props.value === props.value) {
        label = child.props.label;
      } else if (child.type === CarbonSelect.Submenu && child.props.children) {
        const sublabel = getLabel(child.props.children);
        if (sublabel) {
          label = child.props.label + ' : ' + sublabel;
        }
      }
    });

    return label;
  };

  const onChange = (evt: BaseSyntheticEvent) => {
    const value = (evt.target as HTMLElement | null)
      ?.closest('[value]')
      ?.getAttribute('value') as T | null;
    if (value) {
      props.onChange?.(value);
    }
  };

  const children = ignoreFragments(props.children);

  return (
    <div className={classnames('CarbonSelect', props.className)}>
      {props.labelText && (
        <div className="cds--text-input__label-wrapper">
          <label htmlFor={props.id} className="cds--label">
            {props.labelText}
          </label>
        </div>
      )}
      <MenuDropdown
        {...rest}
        onChange={(e: any) => onChange(e)}
        label={getLabel() || t('common.select')}
      >
        {renderChildrenWithProps(children)}
      </MenuDropdown>
    </div>
  );
}

type SubmenuProps = Omit<ComponentProps<typeof MenuItemSelectable>, 'label'> & {
  label?: string | JSX.Element[];
};

CarbonSelect.Submenu = function Submenu(props: SubmenuProps) {
  return (
    <MenuItemSelectable className="Submenu" {...props} label={props.label?.toString() || ''}>
      {props.children}
    </MenuItemSelectable>
  );
};

type OptionProps<T extends object | string | number = string> = SubmenuProps & {
  value?: T;
};

CarbonSelect.Option = function Option<T extends object | string | number = string>(
  props: OptionProps<T>
) {
  return (
    <MenuItemSelectable className="Option" {...props} label={props.label?.toString() || ''}>
      {props.children}
    </MenuItemSelectable>
  );
};
