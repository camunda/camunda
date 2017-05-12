import {get} from 'http';
import {dispatchAction} from 'view-utils';
import {addNotification} from 'notifications';
import {interval} from 'utils';
import {createLoadingProgressAction, createLoadingProgressResultAction, createLoadingProgressErrorAction} from './reducer';

const REFRESH_TIME = 5000;

export function loadProgress() {
  dispatchAction(createLoadingProgressAction());

  const cancelTask = interval(progressTask, REFRESH_TIME);

  progressTask();

  function progressTask() {
    get('/api/status/import-progress')
      .then(response => response.json())
      .then(result => {
        if (+result.progress === 100) {
          cancelTask();
        }

        dispatchAction(createLoadingProgressResultAction(result));
      })
      .catch(err => {
        cancelTask();
        addNotification({
          status: 'Could not load import progress',
          isError: true
        });
        dispatchAction(createLoadingProgressErrorAction(err));
      });
  }
}
