/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  ReactElement,
  createRef,
  ChangeEvent,
  Component,
  Children,
  isValidElement,
  ReactNode,
} from 'react';
import classnames from 'classnames';

import {Input, Button, Icon, Dropdown} from 'components';
import {t} from 'translation';

import OptionsList from './OptionsList';

import './Typeahead.scss';

interface TypeaheadProps<T> {
  children: ReactNode;
  initialValue?: T;
  value?: T;
  onSearch: (query: string) => void;
  onChange: (value: T) => void;
  onOpen: () => void;
  onClose: () => void;
  placeholder?: string | JSX.Element[];
  disabled?: boolean;
  loading?: boolean;
  hasMore?: boolean;
  async?: boolean;
  typedOption?: boolean;
  noValuesMessage?: string | JSX.Element[];
  className?: string;
}

interface TypeaheadState<T> {
  query: string;
  open: boolean;
  selected?: T;
}

const defaultState = {
  query: '',
  open: false,
};
export default class Typeahead<T = unknown> extends Component<
  TypeaheadProps<T>,
  TypeaheadState<T>
> {
  static defaultProps = {
    onSearch: () => {},
    onChange: () => {},
    onOpen: () => {},
    onClose: () => {},
  };

  state: TypeaheadState<T> = defaultState;

  optionClicked = false;

  input = createRef<HTMLInputElement>();

  onTextChange = (evt: ChangeEvent<HTMLInputElement>) => {
    this.setState({query: evt.target.value, open: true});
    this.props.onSearch(evt.target.value);
  };

  componentDidMount() {
    const {initialValue, value} = this.props;

    this.findAndSelect(initialValue);
    this.findAndSelect(value);
  }

  componentDidUpdate(prevProps: TypeaheadProps<T>) {
    if (prevProps.value !== this.props.value) {
      if (typeof this.props.value === 'undefined') {
        this.setState(defaultState);
        this.props.onSearch('');
      } else {
        this.findAndSelect(this.props.value);
      }
    }
    if (!prevProps.initialValue && this.props.initialValue) {
      this.findAndSelect(this.props.initialValue);
    }
  }

  findAndSelect = (value?: T) => {
    const {children, typedOption} = this.props;
    const foundOption = Children.toArray(children).find(
      (option) => (option as ReactElement).props.value === value
    );

    if (typedOption) {
      this.setState({
        selected: value,
        query: value?.toString() || '',
      });
    } else if (isValidElement(foundOption)) {
      const {label, children} = foundOption.props;
      const selected = label || children;
      this.setState({
        selected,
        query: selected,
      });
    }
  };

  selectOption = ({props: {label, children, value}}: ReactElement) => {
    const selected = label || children;
    this.setState({
      selected,
      query: selected,
      open: false,
    });
    this.props.onChange(value);
    this.optionClicked = false;
  };

  getPlaceholderText = (isEmpty: boolean): string | JSX.Element[] | undefined => {
    const {placeholder, disabled, noValuesMessage} = this.props;
    const {selected} = this.state;
    if (selected) {
      return `${selected}`;
    }
    if (disabled || isEmpty) {
      return noValuesMessage || t('common.notFound');
    }
    return placeholder;
  };

  open = () => {
    this.props.onOpen();
    this.setState({open: true, query: ''});
  };

  close = () => {
    this.props.onClose();
    this.setState(({selected}) => ({open: false, query: selected?.toString() || ''}));
  };

  render() {
    const {children, disabled, loading, hasMore, async, typedOption, className} = this.props;
    const {query, open, selected} = this.state;
    const isEmpty = !loading && !query && !typedOption && Children.count(children) === 0;
    const isInputDisabled = isEmpty || disabled;

    return (
      <div className={classnames(className, 'Typeahead')}>
        <Input
          type="text"
          className={classnames('typeaheadInput', {selectionVisible: open && selected})}
          value={query}
          onFocus={this.open}
          onBlur={(evt) => {
            if (!this.optionClicked) {
              this.close();
            }
          }}
          onChange={this.onTextChange}
          ref={this.input}
          placeholder={this.getPlaceholderText(isEmpty)}
          disabled={isInputDisabled}
        />
        <Button
          tabIndex={-1}
          className="optionsButton"
          onClick={() => this.input.current?.focus()}
          disabled={isInputDisabled}
        >
          <Icon type="down" className="downIcon" />
        </Button>
        <OptionsList
          async={async}
          open={open}
          onClose={this.close}
          onOpen={this.open}
          filter={query}
          onSelect={this.selectOption}
          input={this.input.current}
          onMouseDown={() => (this.optionClicked = true)}
          loading={loading}
          hasMore={hasMore}
          typedOption={typedOption}
        >
          {children}
        </OptionsList>
      </div>
    );
  }

  static Option = Dropdown.Option;

  static Highlight = function Highlight(props: {children: string; matchFromStart?: boolean}) {
    return <>{props.children}</>;
  };
}
