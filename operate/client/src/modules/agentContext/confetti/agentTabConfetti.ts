/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import confetti from 'canvas-confetti';

let firedInThisPage = false;

function hasFired(): boolean {
  return firedInThisPage;
}

function markFired() {
  firedInThisPage = true;
}

function burstConfettiFromElement(anchor: HTMLElement) {
  const rect = anchor.getBoundingClientRect();
  const x = (rect.left + rect.width * 0.5) / window.innerWidth;
  const y = (rect.top + rect.height * 0.5) / window.innerHeight;

  // A more dramatic, directional burst that looks like it comes out of the tab.
  const common = {
    origin: {x, y},
    startVelocity: 45,
    spread: 70,
    ticks: 160,
    gravity: 1.2,
    scalar: 0.95,
    zIndex: 9999,
  };

  confetti({
    ...common,
    particleCount: 90,
    angle: 60,
    colors: ['#0f62fe', '#42be65', '#f1c21b', '#ff832b', '#a56eff'],
  });

  confetti({
    ...common,
    particleCount: 90,
    angle: 120,
    colors: ['#0f62fe', '#42be65', '#f1c21b', '#ff832b', '#a56eff'],
  });

  // Small finishing sparkle.
  confetti({
    origin: {x, y},
    particleCount: 35,
    spread: 110,
    startVelocity: 20,
    ticks: 120,
    gravity: 1.0,
    scalar: 0.75,
    zIndex: 9999,
    colors: ['#ffffff', '#dde1e6', '#0f62fe'],
  });
}

/**
 * Fire the confetti only once per page load.
 */
function fireAgentTabConfettiOnce(anchor: HTMLElement | null) {
  if (!anchor) {
    return;
  }

  if (hasFired()) {
    return;
  }

  markFired();
  burstConfettiFromElement(anchor);
}

export {fireAgentTabConfettiOnce};
