/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Children, cloneElement, Component, ComponentProps, ReactNode, UIEvent} from 'react';

import {Dropdown} from 'components';
import {ignoreFragments, isReactElement} from 'services';
import classnames from 'classnames';
import {t} from 'translation';

interface Selectprops extends Omit<ComponentProps<typeof Dropdown>, 'label'> {
  value?: string;
  onChange?: (value: string) => void;
  label?: string | JSX.Element[];
}

export default class Select extends Component<Selectprops> {
  renderChildrenWithProps = (children: ReactNode) => {
    return Children.toArray(children)
      .filter(isReactElement)
      .map((child) => {
        const props:
          | ComponentProps<typeof Dropdown.Option>
          | ComponentProps<typeof Dropdown.Submenu> = {};

        if (child.type === Select.Submenu) {
          props.checked = Children.toArray(child.props.children).some(
            (child) => isReactElement(child) && child.props.value === this.props.value
          );

          props.children = this.renderChildrenWithProps(child.props.children);
        } else {
          props.onClick = this.onChange;
          props.checked = child && child.props.value === this.props.value;
        }
        return child && cloneElement(child, props);
      });
  };

  getLabel = (children = this.props.children) => {
    let label;

    Children.forEach(ignoreFragments(children), (child) => {
      if (child?.props.value === this.props.value) {
        label = child.props.label || child.props.children;
      } else if (child?.type === Select.Submenu && child?.props.children) {
        const sublabel = this.getLabel(child.props.children);
        if (sublabel) {
          label = child.props.label + ' : ' + sublabel;
        }
      }
    });

    return label;
  };

  onChange = (evt: UIEvent<HTMLElement>) => {
    const value = (evt.target as HTMLElement | null)?.closest('[value]')?.getAttribute('value');
    if (value && this.props.onChange) {
      this.props.onChange(value);
    }
  };

  render() {
    const children = ignoreFragments(this.props.children);

    return (
      <Dropdown
        {...this.props}
        label={this.props.label || this.getLabel() || t('common.select')}
        className={classnames('Select', this.props.className)}
      >
        {this.renderChildrenWithProps(children)}
      </Dropdown>
    );
  }

  static Option = function Option(props: ComponentProps<typeof Dropdown.Option>) {
    return <Dropdown.Option {...props}>{props.children}</Dropdown.Option>;
  };

  static Submenu = function Submenu(props: ComponentProps<typeof Dropdown.Submenu>) {
    return <Dropdown.Submenu {...props}>{props.children}</Dropdown.Submenu>;
  };
}
