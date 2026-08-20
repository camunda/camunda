import {useLocation} from '@docusaurus/router';
import React, {useEffect} from 'react';

const buildItemId = (item, index) => {
  const normalized = item.textContent?.replace(/\s+/g, ' ').trim() ?? `item-${index}`;
  let hash = 0;
  for (let position = 0; position < normalized.length; position += 1) {
    hash = (hash << 5) - hash + normalized.charCodeAt(position);
    hash |= 0;
  }
  return `${index}-${Math.abs(hash)}`;
};

const collectCheckboxesBetweenHeadings = (startHeadingId, endHeadingId) => {
  const startHeading = document.getElementById(startHeadingId);
  if (!startHeading) {
    return [];
  }

  const checkboxes = [];
  let currentNode = startHeading.nextElementSibling;

  while (currentNode) {
    if (currentNode.id === endHeadingId) {
      break;
    }

    checkboxes.push(
      ...currentNode.querySelectorAll('li.task-list-item > input[type="checkbox"]'),
    );
    currentNode = currentNode.nextElementSibling;
  }

  return checkboxes;
};

export default function PersistentTaskListEnabler({
  storageKey,
  version = '1',
  startHeadingId,
  endHeadingId,
}) {
  const location = useLocation();

  useEffect(() => {
    if (typeof window === 'undefined') {
      return undefined;
    }

    const checkboxNodes = collectCheckboxesBetweenHeadings(startHeadingId, endHeadingId);
    if (checkboxNodes.length === 0) {
      return undefined;
    }

    const localStorageKey = `${storageKey}:v${version}`;
    let persistedState = {};
    try {
      const rawState = window.localStorage.getItem(localStorageKey);
      persistedState = rawState ? JSON.parse(rawState) : {};
    } catch {
      persistedState = {};
    }

    const cleanupHandlers = checkboxNodes.map((checkboxNode, index) => {
      const listItem = checkboxNode.closest('li');
      if (!listItem) {
        return () => {};
      }

      const itemId = buildItemId(listItem, index);
      checkboxNode.disabled = false;
      checkboxNode.checked = Boolean(persistedState[itemId]);

      const onChange = () => {
        persistedState[itemId] = checkboxNode.checked;
        window.localStorage.setItem(localStorageKey, JSON.stringify(persistedState));
      };

      checkboxNode.addEventListener('change', onChange);
      return () => checkboxNode.removeEventListener('change', onChange);
    });

    return () => {
      cleanupHandlers.forEach((cleanupHandler) => cleanupHandler());
    };
  }, [endHeadingId, location.pathname, startHeadingId, storageKey, version]);

  return null;
}