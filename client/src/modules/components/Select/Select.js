/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {Dropdown} from 'components';
import classnames from 'classnames';
import {t} from 'translation';

import {ignoreFragments} from './service';

export default class Select extends React.Component {
  renderChildrenWithProps = (children) => {
    return React.Children.map(children, (child) => {
      const props = {};

      if (child && child.type === Select.Submenu) {
        props.checked = React.Children.toArray(child.props.children).some(
          (child) => child && child.props.value === this.props.value
        );

        props.children = this.renderChildrenWithProps(child.props.children);
      } else {
        props.onClick = this.onChange;
        props.checked = child && child.props.value === this.props.value;
      }

      return child && React.cloneElement(child, props);
    });
  };

  getLabel = (children = this.props.children) => {
    let label;

    React.Children.forEach(ignoreFragments(children), (child) => {
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

  onChange = (evt) => {
    const value = evt.target?.closest('[value]')?.getAttribute('value');
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
}

Select.Option = function Option(props) {
  return <Dropdown.Option {...props}>{props.children}</Dropdown.Option>;
};

Select.Submenu = function Submenu(props) {
  return <Dropdown.Submenu {...props}>{props.children}</Dropdown.Submenu>;
};
