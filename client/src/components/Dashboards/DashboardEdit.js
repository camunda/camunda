/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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

import {AddButton} from './AddButton';
import {DeleteButton} from './DeleteButton';
import DragOverlay from './DragOverlay';
import EditButton from './EditButton';
import {AutoRefreshSelect} from './AutoRefresh';

import {FiltersEdit, AddFiltersButton} from './filters';
import {convertFilterToDefaultValues, getDefaultFilter} from './service';

import './DashboardEdit.scss';

export class DashboardEdit extends React.Component {
  constructor(props) {
    super(props);

    const {name, initialAvailableFilters, initialTiles, refreshRateSeconds} = props;
    this.state = {
      tiles: initialTiles,
      availableFilters: initialAvailableFilters || [],
      refreshRateSeconds,
      filter: getDefaultFilter(initialAvailableFilters),
      name: name,
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

      // We need to give the library time to process the grab before
      // artificially generating mousemove events
      setTimeout(this.autoScroll);
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
      this.draggedItem.dispatchEvent(createEvent('mousemove', this.mousePosition));
      this.autoScrollHandle = requestAnimationFrame(this.autoScroll);
    }
  };

  clearDraggedItem = () => {
    this.draggedItem = null;

    // since we need to timeout the start of the autoscroll, we also need
    // to timeout the cancel to prevent a "cancel before start" bug
    setTimeout(() => {
      cancelAnimationFrame(this.autoScrollHandle);
    });
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

  addReport = (newReport) => {
    this.setState({tiles: update(this.state.tiles, {$push: [newReport]})}, () => {
      const node = document.querySelector('.react-grid-layout').lastChild;
      const nodePos = node.getBoundingClientRect();

      // dispatch a mouse event to automatically grab the new report for positioning
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

      window.setTimeout(() => {
        node.dispatchEvent(createEvent('mousemove', this.mousePosition));
        node.dispatchEvent(createEvent('mousemove', this.mousePosition));
      });
    });
  };

  updateReport = (report) => {
    this.setState(({tiles}) => {
      const newReports = tiles.map((oldTile) => {
        if (report.position.x === oldTile.position.x && report.position.y === oldTile.position.y) {
          return report;
        }
        return oldTile;
      });

      return {tiles: newReports};
    });
  };

  deleteReport = ({report: reportToRemove}) => {
    this.setState({
      tiles: this.state.tiles.filter((tile) => tile !== reportToRemove),
    });
  };

  componentDidUpdate(prevProps) {
    if (
      this.state.tiles.every(
        ({id, configuration}) => id || configuration?.external || configuration?.text
      ) &&
      deepEqual(this.state.tiles, this.props.initialTiles) &&
      deepEqual(this.state.availableFilters, this.props.initialAvailableFilters) &&
      this.state.name === this.props.name
    ) {
      nowPristine();
    } else {
      nowDirty(t('dashboard.label'), this.save);
    }

    if (prevProps.initialTiles !== this.props.initialTiles) {
      // initial reports might change because of a save without leaving the edit mode
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
      const {name, tiles, availableFilters, filter, refreshRateSeconds} = this.state;

      nowPristine();
      promises.push(
        this.props.saveChanges(
          name,
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

  editReport = (report) => {
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
          const reportIdx = this.state.tiles.indexOf(report);
          await this.save(true);
          const savedDashboardPath = this.props.location.pathname;
          const reportId = this.state.tiles[reportIdx].id;
          history.push('report/' + reportId + '/edit?returnTo=' + savedDashboardPath);
        }
      );
    } else {
      history.push('report/' + report.id + '/edit?returnTo=' + location.pathname);
    }
  };

  render() {
    const {lastModifier, lastModified, isNew} = this.props;
    const {tiles, name, availableFilters, refreshRateSeconds, filter} = this.state;

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
          >
            <AddButton addReport={this.addReport} existingReport={tiles?.[0]} />
            <AddFiltersButton
              reports={optimizeReports}
              persistReports={() => this.save(true)}
              availableFilters={availableFilters}
              setAvailableFilters={(availableFilters) => this.setState({availableFilters})}
            />
            <AutoRefreshSelect
              refreshRateMs={refreshRateSeconds * 1000}
              onChange={(refreshRateMs) =>
                this.setState({refreshRateSeconds: refreshRateMs / 1000 || null})
              }
            />
            <div className="separator" />
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
            disableReportInteractions
            tiles={tiles}
            filter={filter}
            loadReport={evaluateReport}
            addons={[
              <DragOverlay key="DragOverlay" />,
              <DeleteButton key="DeleteButton" deleteReport={this.deleteReport} />,
              <EditButton key="EditButton" onClick={this.editReport} />,
            ]}
            onLayoutChange={this.updateLayout}
            onReportUpdate={this.updateReport}
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
