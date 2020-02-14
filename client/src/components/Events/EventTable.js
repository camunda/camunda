/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import debounce from 'debounce';
import classnames from 'classnames';
import deepEqual from 'deep-equal';

import {Table, LoadingIndicator, Input, Select, Switch, Icon} from 'components';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';
import {t} from 'translation';

import {loadEvents} from './service';

import './EventTable.scss';
import EventsSources from './EventsSources';

const asMapping = ({group, source, eventName}) => ({group, source, eventName});

export default withErrorHandling(
  class EventTable extends React.Component {
    container = React.createRef();

    state = {
      events: null,
      searchQuery: '',
      showSuggested: true
    };

    async componentDidMount() {
      this.loadEvents(this.state.searchQuery);
    }

    loadEvents = searchQuery => {
      const {selection, xml, mappings, eventSources} = this.props;

      this.setState({events: null});

      let payload = undefined;
      if (this.state.showSuggested && this.getNumberOfPotentialMappings(selection)) {
        payload = {
          targetFlowNodeId: selection.id,
          xml,
          mappings,
          eventSources
        };
      }

      this.props.mightFail(
        loadEvents(payload, searchQuery),
        events => this.setState({events}),
        showError
      );
    };
    loadEventsDebounced = debounce(this.loadEvents, 300);

    getNumberOfPotentialMappings = node => {
      if (!node) {
        return 0;
      }
      if (node.$instanceOf('bpmn:Task')) {
        return 2;
      } else if (node.$instanceOf('bpmn:Event')) {
        return 1;
      } else {
        return 0;
      }
    };

    mappedAs = event => {
      const mappings = Object.values(this.props.mappings);

      for (let i = 0; i < mappings.length; i++) {
        if (mappings[i]) {
          const {start, end} = mappings[i];
          const eventAsMapping = asMapping(event);

          if (deepEqual(start, eventAsMapping)) {
            return 'start';
          }
          if (deepEqual(end, eventAsMapping)) {
            return 'end';
          }
        }
      }
    };

    searchFor = searchQuery => {
      this.setState({searchQuery, events: null});
      this.loadEventsDebounced(searchQuery);
    };

    componentDidUpdate(prevProps, prevState) {
      this.updateTableAfterSelectionChange(prevProps);
      if (prevState.events === null && this.state.events !== null) {
        this.scrollToSelectedElement();
      }
    }

    updateTableAfterSelectionChange = prevProps => {
      const {selection} = this.props;
      const {showSuggested, searchQuery} = this.state;

      const prevSelection = prevProps.selection;

      const selectionMade = !prevSelection && selection;
      const selectionChanged = selection && prevSelection && prevSelection.id !== selection.id;
      const selectionCleared = prevSelection && !selection;

      if (selectionMade || selectionChanged || selectionCleared) {
        if (showSuggested) {
          this.loadEvents(searchQuery);
        } else {
          this.scrollToSelectedElement();
        }
      }
    };

    scrollToSelectedElement = () => {
      const mappedElement =
        this.container.current && this.container.current.querySelector('.mapped');
      if (mappedElement) {
        mappedElement.scrollIntoView({behavior: 'smooth', block: 'nearest'});
      }
    };

    render() {
      const {events, searchQuery, showSuggested} = this.state;
      const {selection, onMappingChange, mappings, eventSources} = this.props;

      const {start, end} = (selection && mappings[selection.id]) || {};
      const numberOfMappings = !!start + !!end;
      const numberOfPotentialMappings = this.getNumberOfPotentialMappings(selection);

      const disabled = numberOfPotentialMappings <= numberOfMappings;
      const camundaSource = eventSources.filter(src => src.type !== 'external');
      const externalEvents = eventSources.some(src => src.type === 'external');
      const disableSuggestions = camundaSource.length;

      return (
        <div className="EventTable" ref={this.container}>
          <div className="header">
            <b>{t('events.list')}</b>
            <Switch
              disabled={disableSuggestions}
              checked={!disableSuggestions && showSuggested}
              onChange={({target: {checked}}) =>
                this.setState({showSuggested: checked}, () => this.loadEvents(searchQuery))
              }
              label={t('events.table.showSuggestions')}
            />
            <EventsSources sources={eventSources} onChange={this.props.onSourcesChange} />
            <div className="searchContainer">
              <Icon className="searchIcon" type="search" />
              <Input
                required
                type="text"
                className="searchInput"
                placeholder={t('home.search.name')}
                value={searchQuery}
                onChange={({target: {value}}) => this.searchFor(value)}
                onClear={() => this.searchFor('')}
              />
            </div>
          </div>
          <Table
            head={[
              'checked',
              t('events.table.mapping'),
              t('events.table.name'),
              t('events.table.group'),
              t('events.table.source'),
              t('events.table.count')
            ]}
            body={
              events
                ? events
                    .filter(
                      event =>
                        !this.mappedAs(event) ||
                        deepEqual(start, asMapping(event)) ||
                        deepEqual(end, asMapping(event))
                    )
                    .map(event => {
                      const {group, source, eventName, count, suggested} = event;
                      const mappedAs = this.mappedAs(event);
                      const eventAsMapping = asMapping(event);
                      const isDisabled = disabled && !mappedAs;

                      return {
                        content: [
                          <Input
                            type="checkbox"
                            checked={!!mappedAs}
                            disabled={isDisabled}
                            onChange={({target: {checked}}) =>
                              onMappingChange(eventAsMapping, checked)
                            }
                          />,
                          mappedAs ? (
                            <Select
                              value={mappedAs}
                              onOpen={isOpen => {
                                if (isOpen) {
                                  // due to how we integrate Dropdowns in React Table, we need to manually
                                  // adjust to the scroll offset
                                  const container = this.container.current;
                                  container.querySelector(
                                    '.Dropdown.is-open .menu'
                                  ).style.marginTop =
                                    -container.querySelector('.rt-tbody').scrollTop + 'px';
                                }
                              }}
                              onChange={value =>
                                mappedAs !== value && onMappingChange(eventAsMapping, true, value)
                              }
                            >
                              <Select.Option
                                value="end"
                                disabled={mappedAs !== 'end' && numberOfMappings === 2}
                              >
                                {t('events.table.end')}
                              </Select.Option>
                              <Select.Option
                                value="start"
                                disabled={mappedAs !== 'start' && numberOfMappings === 2}
                              >
                                {t('events.table.start')}
                              </Select.Option>
                            </Select>
                          ) : (
                            '--'
                          ),
                          eventName,
                          group,
                          source,
                          count
                        ],
                        props: {
                          className: classnames({
                            disabled: isDisabled,
                            mapped: mappedAs,
                            suggested
                          })
                        }
                      };
                    })
                : []
            }
            disablePagination
            noData={
              <>
                {!events && <LoadingIndicator />}
                {events && searchQuery && t('events.table.noResults')}
                {events && !!events.length && !searchQuery && t('events.table.allMapped')}
                {events &&
                  !events.length &&
                  !searchQuery &&
                  !externalEvents &&
                  t('events.sources.empty')}
              </>
            }
            noHighlight={disabled}
          />
        </div>
      );
    }
  }
);
