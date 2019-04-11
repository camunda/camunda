/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get, del, post} from 'request';

export async function isSharingEnabled() {
  const response = await get(`api/share/isEnabled`);
  const json = await response.json();
  return json.enabled;
}

export async function shareDashboard(dashboardId) {
  const body = {
    dashboardId
  };
  const response = await post(`api/share/dashboard`, body);

  const json = await response.json();
  return json.id;
}

export async function getSharedDashboard(reportId) {
  const response = await get(`api/share/dashboard/${reportId}`);

  if (response.status > 201) {
    return '';
  } else {
    const json = await response.json();
    return json.id;
  }
}

export async function revokeDashboardSharing(id) {
  return await del(`api/share/dashboard/${id}`);
}

export async function isAuthorizedToShareDashboard(dashboardId) {
  try {
    const response = await get(`api/share/dashboard/${dashboardId}/isAuthorizedToShare`);
    return response.status === 200;
  } catch (error) {
    return false;
  }
}

export function getOccupiedTiles(reports) {
  const occupiedTiles = {};

  reports.forEach(({position, dimensions}) => {
    for (let x = position.x; x < position.x + dimensions.width; x++) {
      for (let y = position.y; y < position.y + dimensions.height; y++) {
        occupiedTiles[x] = occupiedTiles[x] || {};
        occupiedTiles[x][y] = true;
      }
    }
  });

  return occupiedTiles;
}

export function snapInPosition({
  tileDimensions: {columns, outerWidth, outerHeight, innerWidth},
  report: {position, dimensions},
  changes
}) {
  // map into tile units
  const delta = {
    x: Math.round((changes.x || 0) / outerWidth),
    y: Math.round((changes.y || 0) / outerHeight),
    width: Math.round((changes.width || 0) / outerWidth),
    height: Math.round((changes.height || 0) / outerHeight)
  };

  // get the new final placement of the report in tile coordinates
  const newPlacement = {
    position: {
      x: position.x + delta.x,
      y: position.y + delta.y
    },
    dimensions: {
      width: dimensions.width + delta.width,
      height: dimensions.height + delta.height
    }
  };

  // do not allow placing a report outside of the grid boundaries
  newPlacement.position.x = Math.max(newPlacement.position.x, 0);
  newPlacement.position.x = Math.min(
    newPlacement.position.x,
    columns - newPlacement.dimensions.width
  );
  newPlacement.position.y = Math.max(newPlacement.position.y, 0);

  // do not allow making a report wider than the grid
  newPlacement.dimensions.width = Math.min(
    newPlacement.dimensions.width,
    columns - newPlacement.position.x
  );

  // do not allow a non-positive dimensions
  newPlacement.dimensions.width = Math.max(newPlacement.dimensions.width, 1);
  newPlacement.dimensions.height = Math.max(newPlacement.dimensions.height, 1);

  return newPlacement;
}

export function collidesWithReport({
  placement: {
    position: {x: left, y: top},
    dimensions: {width, height}
  },
  reports
}) {
  const occupiedTiles = getOccupiedTiles(reports);

  for (let x = left; x < left + width; x++) {
    for (let y = top; y < top + height; y++) {
      if (occupiedTiles[x] && occupiedTiles[x][y]) {
        return true;
      }
    }
  }

  return false;
}

export function applyPlacement({
  placement: {
    position: {x, y},
    dimensions: {width, height}
  },
  tileDimensions: {outerWidth, outerHeight, innerWidth},
  node: {style}
}) {
  const margin = outerWidth - innerWidth;

  style.left = x * outerWidth + margin / 2 - 1 + 'px';
  style.top = y * outerHeight + margin / 2 - 1 + 'px';
  style.width = width * outerWidth - margin + 1 + 'px';
  style.height = height * outerHeight - margin + 1 + 'px';
}
