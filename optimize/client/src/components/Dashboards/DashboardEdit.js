/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {withRouter} from 'react-router-dom';
import update from 'immutability-helper';
import deepEqual from 'fast-deep-equal';

import {evaluateReport} from 'services';
import {DashboardRenderer, EntityNameForm} from 'components';
import {t} from 'translation';
import {nowDirty, nowPristine, isDirty} from 'saveGuard';
import {showPrompt} from 'prompt';
import {track} from 'tracking';

import {AddButton} from './AddButton';
import {EditButton} from './EditButton';
import {CopyButton} from './CopyButton';
import {DeleteButton} from './DeleteButton';
import DragOverlay from './DragOverlay';
import {AutoRefreshSelect} from './AutoRefresh';

import {FiltersEdit, AddFiltersButton} from './filters';
import {convertFilterToDefaultValues, getDefaultFilter} from './service';

import './DashboardEdit.scss';

export class DashboardEdit extends React.Component {
  constructor(props) {
    super(props);

    const {id, name, description, initialAvailableFilters, initialTiles, refreshRateSeconds} =
      props;
    this.state = {
      tiles: initialTiles,
      availableFilters: initialAvailableFilters || [],
      refreshRateSeconds,
      filter: getDefaultFilter(initialAvailableFilters),
      id,
      name,
      description,
    };
  }

  contentContainer = React.createRef();
  waitingForDashboardSave = [];

  mousePosition = {x: 0, y: 0};
  mouseTracker = (evt) => {
    this.mousePosition.x = evt.clientX;
    this.mousePosition.y = evt.clientY;
  };

  componentDidMount() {
    document.addEventListener('mousedown', this.setDraggedItem);
    document.addEventListener('mousemove', this.mouseTracker);
    document.addEventListener('mouseup', this.clearDraggedItem);
  }

  componentWillUnmount() {
    document.removeEventListener('mousedown', this.setDraggedItem);
    document.removeEventListener('mousemove', this.mouseTracker);
    document.removeEventListener('mouseup', this.clearDraggedItem);
  }

  draggedItem = null;
  setDraggedItem = (evt) => {
    this.draggedItem = evt.target.closest('.grid-entry');
    if (this.draggedItem) {
      // We need to prevent the browser scroll that occurs when resizing
      evt.preventDefault();
      this.autoScroll();
    }
  };

  autoScroll = () => {
    if (this.draggedItem) {
      const container = this.contentContainer.current;
      const containerTop = container.offsetTop;
      const containerBottom = containerTop + container.offsetHeight;

      const deltaTop = this.mousePosition.y - containerTop;
      const deltaBottom = containerBottom - this.mousePosition.y;
      if (deltaTop < 30) {
        container.scrollTop -= (30 - deltaTop) / 5;
      } else if (deltaBottom < 30) {
        container.scrollTop += (30 - deltaBottom) / 5;
      }

      // We need to give the library time to process the grab before
      // artificially generating mousemove events
      setTimeout(() => {
        this.draggedItem.dispatchEvent(createEvent('mousemove', this.mousePosition));
      });

      this.autoScrollHandle = requestAnimationFrame(this.autoScroll);
    }
  };

  clearDraggedItem = () => {
    this.draggedItem = null;
    cancelAnimationFrame(this.autoScrollHandle);
  };

  updateLayout = (layout) => {
    this.setState(({tiles}) => {
      const newTiles = tiles.map((oldTile, idx) => {
        const newPosition = layout[idx];

        return {
          ...oldTile,
          position: {x: newPosition.x, y: newPosition.y},
          dimensions: {height: newPosition.h, width: newPosition.w},
        };
      });

      return {tiles: newTiles};
    });
  };

  updateName = ({target: {value}}) => {
    this.setState({name: value});
  };

  updateDescription = (description) => {
    track('editDescription', {entity: 'dashboard', entityId: this.state.id});
    this.setState({description});
  };

  addTile = (newTile) => {
    this.setState({tiles: update(this.state.tiles, {$push: [newTile]})}, () => {
      const node = document.querySelector('.react-grid-layout').lastChild;
      const nodePos = node.getBoundingClientRect();

      // dispatch a mouse event to automatically grab the new tile for positioning
      node.dispatchEvent(
        createEvent('mousedown', {
          x: nodePos.x + nodePos.width / 2,
          y: nodePos.y + nodePos.height / 2,
        })
      );

      // prevent the next mousedown event (it confuses the grid library)
      node.addEventListener(
        'mousedown',
        (evt) => {
          evt.preventDefault();
          evt.stopPropagation();
        },
        {capture: true, once: true}
      );
    });

    track(getEventName('create', newTile.type), {entityId: newTile.id});
  };

  updateTile = (tile) => {
    // if the tile is an optimize report, we need to handle it differently because it is a separate entity
    // All the other tiles are not real entities and are just a part of dashboard configuration
    if (tile.type === 'optimize_report') {
      this.updateOptimizeReportTile(tile);
    } else {
      this.setState(({tiles}) => {
        const newTiles = tiles.map((oldTile) => {
          if (tile.position.x === oldTile.position.x && tile.position.y === oldTile.position.y) {
            return tile;
          }
          return oldTile;
        });

        track(getEventName('update', tile.type), {entityId: tile.id});
        return {tiles: newTiles};
      });
    }
  };

  deleteTile = (tileToRemove) => {
    this.setState({
      tiles: this.state.tiles.filter((tile) => tile !== tileToRemove),
    });
    track(getEventName('delete', tileToRemove.type), {entityId: tileToRemove.id});
  };

  componentDidUpdate(prevProps) {
    if (
      this.state.tiles.every(
        ({id, configuration}) => id || configuration?.external || configuration?.text
      ) &&
      deepEqual(this.state.tiles, this.props.initialTiles) &&
      deepEqual(this.state.availableFilters, this.props.initialAvailableFilters) &&
      this.state.name === this.props.name &&
      this.state.description === this.props.description
    ) {
      nowPristine();
    } else {
      nowDirty(t('dashboard.label'), this.save);
    }

    if (prevProps.initialTiles !== this.props.initialTiles) {
      // initial tiles might change because of a save without leaving the edit mode
      // This happens for example if the dashboard is saved for the variable filter
      this.setState({tiles: this.props.initialTiles}, () => {
        this.waitingForDashboardSave.forEach((resolve) => resolve());
        this.waitingForDashboardSave.length = 0;
      });
      nowPristine();
    }
  }

  save = (stayInEditMode) => {
    return new Promise((resolve) => {
      const promises = [];
      const {name, description, tiles, availableFilters, filter, refreshRateSeconds} = this.state;

      nowPristine();
      promises.push(
        this.props.saveChanges(
          name,
          description,
          tiles,
          availableFilters.map((availableFilter) => {
            return {
              type: availableFilter.type,
              data: {
                ...availableFilter.data,
                defaultValues: convertFilterToDefaultValues(availableFilter, filter),
              },
            };
          }),
          refreshRateSeconds,
          stayInEditMode
        )
      );

      if (stayInEditMode) {
        promises.push(
          new Promise((resolve) => {
            this.waitingForDashboardSave.push(resolve);
          })
        );
      }

      Promise.all(promises).then(resolve);
    });
  };

  updateOptimizeReportTile = (tile) => {
    const {history, location} = this.props;
    if (isDirty()) {
      showPrompt(
        {
          title: t('dashboard.saveModal.unsaved'),
          body: t('dashboard.saveModal.text'),
          yes: t('common.saveContinue'),
          no: t('common.cancel'),
        },
        async () => {
          // unsaved reports don't have ids yet. As their report object gets overwritten on save
          // we keep track of their position in the reports array instead to match the old
          // report object with the new one that has an id after the save
          const tileIdx = this.state.tiles.indexOf(tile);
          await this.save(true);
          const savedDashboardPath = this.props.location.pathname;
          const tileId = this.state.tiles[tileIdx].id;
          history.push('report/' + tileId + '/edit?returnTo=' + savedDashboardPath);
        }
      );
    } else {
      history.push('report/' + tile.id + '/edit?returnTo=' + location.pathname);
    }
  };

  render() {
    const {lastModifier, lastModified, isNew} = this.props;
    const {tiles, name, description, availableFilters, refreshRateSeconds, filter} = this.state;

    const optimizeReports = tiles?.filter(({id, report}) => !!id || !!report);

    return (
      <div className="DashboardEdit">
        <div className="header">
          <EntityNameForm
            name={name}
            lastModified={lastModified}
            lastModifier={lastModifier}
            isNew={isNew}
            entity="Dashboard"
            onChange={this.updateName}
            onSave={this.save}
            onCancel={nowPristine}
            description={description}
            onDescriptionChange={this.updateDescription}
          >
            <AddButton addTile={this.addTile} existingReport={optimizeReports?.[0]} />
            <AddFiltersButton
              size="md"
              reports={optimizeReports}
              persistReports={() => this.save(true)}
              availableFilters={availableFilters}
              setAvailableFilters={(availableFilters) => this.setState({availableFilters})}
            />
            <AutoRefreshSelect
              size="md"
              refreshRateMs={refreshRateSeconds * 1000}
              onChange={(refreshRateMs) =>
                this.setState({refreshRateSeconds: refreshRateMs / 1000 || null})
              }
            />
          </EntityNameForm>
        </div>
        {availableFilters.length > 0 && (
          <FiltersEdit
            reports={optimizeReports}
            persistReports={() => this.save(true)}
            availableFilters={availableFilters}
            filter={filter}
            setFilter={(filter) => this.setState({filter})}
            setAvailableFilters={(availableFilters) => this.setState({availableFilters})}
          />
        )}
        <div className="content" ref={this.contentContainer}>
          <DashboardRenderer
            disableTileInteractions
            tiles={tiles}
            filter={filter}
            addons={[<DragOverlay />, <EditButton />, <CopyButton />, <DeleteButton />]}
            loadTile={evaluateReport}
            onTileAdd={this.addTile}
            onTileUpdate={this.updateTile}
            onTileDelete={this.deleteTile}
            onLayoutChange={this.updateLayout}
          />
        </div>
      </div>
    );
  }
}

export default withRouter(DashboardEdit);

function createEvent(type, position) {
  return new MouseEvent(type, {
    view: window,
    bubbles: true,
    cancelable: true,
    clientX: position.x,
    clientY: position.y,
  });
}

function getEventName(action, eventType) {
  const eventNameTokens = eventType.split('_');
  const eventName = eventNameTokens
    .map((token) => token.charAt(0).toUpperCase() + token.slice(1))
    .join('');
  return action + eventName + 'Tile';
}
