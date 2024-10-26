/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  BaseSyntheticEvent,
  Children,
  cloneElement,
  ComponentProps,
  ReactNode,
  useLayoutEffect,
  useRef,
} from 'react';
import {MenuItemSelectable} from '@carbon/react';
import {MenuDropdown} from '@camunda/camunda-optimize-composite-components';

import {ignoreFragments, isReactElement} from 'services';
import classnames from 'classnames';
import {t} from 'translation';

export interface SelectProps<T extends object | string | number = string>
  extends Omit<ComponentProps<typeof MenuDropdown>, 'label' | 'onChange'> {
  id: string;
  value?: T;
  onChange?: (value: T) => void;
  labelText?: string | JSX.Element[];
  helperText?: string | JSX.Element[];
  placeholder?: string;
}

export default function Select<T extends object | string | number>(props: SelectProps<T>) {
  const {labelText, helperText, ...rest} = props;
  const renderChildrenWithProps = (children: ReactNode) => {
    return Children.toArray(children)
      .filter(isReactElement<SubmenuProps | OptionProps>)
      .map((child) => {
        const newProps: SubmenuProps | OptionProps = {
          ...child.props,
          label: child.props.label?.toString() || '',
        };

        if (child.type === Select.Submenu) {
          newProps.selected = Children.toArray(child.props.children).some(
            (child) => isReactElement(child) && child.props.value === props.value
          );

          newProps.children = renderChildrenWithProps(child.props.children);
        } else if (child.type === Select.Option) {
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
      } else if (child.type === Select.Submenu && child.props.children) {
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
    <div className={classnames('Select', props.className)}>
      {labelText && (
        <div className="cds--text-input__label-wrapper">
          <label htmlFor={props.id} className="cds--label">
            {labelText}
          </label>
        </div>
      )}
      <MenuDropdown
        {...rest}
        onChange={(e: BaseSyntheticEvent) => onChange(e)}
        label={props.placeholder || getLabel() || t('common.select').toString()}
        menuTarget={document.querySelector<HTMLElement>('.fullscreen')}
      >
        {renderChildrenWithProps(children)}
      </MenuDropdown>
      {helperText && !props.invalid && <div className="cds--form__helper-text">{helperText}</div>}
    </div>
  );
}

type SubmenuProps = Omit<ComponentProps<typeof MenuItemSelectable>, 'label'> & {
  label?: string | JSX.Element[];
  children?: ReactNode;
  disabled?: boolean;
};

Select.Submenu = function Submenu(props: SubmenuProps) {
  const submenuRef = useRef<HTMLLIElement>(null);

  useLayoutEffect(() => {
    if (submenuRef.current) {
      const submenu = submenuRef.current.querySelector('.cds--menu') as HTMLLIElement;

      if (submenu) {
        const rect = submenu.getBoundingClientRect();
        submenu.style.marginLeft = `-${rect.width}px`;
      }
    }
  }, []);

  return (
    // To make disabled state work, we can't pass children to it
    <MenuItemSelectable
      ref={submenuRef}
      className="Submenu"
      {...props}
      label={props.label?.toString() || ''}
    >
      {!props.disabled && props.children}
    </MenuItemSelectable>
  );
};

type OptionProps<T extends object | string | number = string> = SubmenuProps & {
  value?: T;
};

Select.Option = function Option<T extends object | string | number = string>(
  props: OptionProps<T>
) {
  return (
    // To make disabled state work, we can't pass children to it
    <MenuItemSelectable className="Option" {...props} label={props.label?.toString() || ''}>
      {!props.disabled && props.children}
    </MenuItemSelectable>
  );
};
