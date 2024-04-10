/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {useStartProcessParams} from 'modules/routing';
import {Content, FormContainer} from './styled';
import {useExternalForm} from 'modules/queries/useExternalForm';
import {useLayoutEffect, useState} from 'react';
import {FormJS} from './FormJS';
import {Skeleton} from './FormJS/Skeleton';
import {useStartExternalProcess} from 'modules/mutations/useStartExternalProcess';
import {logger} from 'modules/utils/logger';
import {tracking} from 'modules/tracking';
import CheckImage from 'modules/images/orange-check-mark.svg';
import ErrorRobotImage from 'modules/images/error-robot.svg';
import {Message} from './Message';
import {match, Pattern} from 'ts-pattern';

const StartProcessFromForm: React.FC = () => {
  const [pageView, setPageView] = useState<
    | 'form'
    | 'submit-success'
    | 'form-not-found'
    | 'failed-submission'
    | 'invalid-form-schema'
  >('form');
  const {bpmnProcessId} = useStartProcessParams();
  const {data, error} = useExternalForm(bpmnProcessId);
  const {mutateAsync: startExternalProcess, reset} = useStartExternalProcess({
    onError: (error) => {
      logger.error(error);
      tracking.track({
        eventName: 'public-start-form-submission-failed',
      });
    },
    onMutate: () => {
      tracking.track({
        eventName: 'public-start-form-submitted',
      });
    },
  });

  useLayoutEffect(() => {
    if (error !== null) {
      tracking.track({
        eventName: 'public-start-form-load-failed',
      });
      setPageView('form-not-found');
    }
  }, [error]);

  useLayoutEffect(() => {
    tracking.track({
      eventName: 'public-start-form-opened',
    });
  }, []);

  useLayoutEffect(() => {
    if (data !== undefined) {
      tracking.track({
        eventName: 'public-start-form-loaded',
      });
      setPageView('form');
    }
  }, [data]);

  return (
    <>
      <Content id="main-content" tabIndex={-1} tagName="main">
        <FormContainer>
          {match({data, pageView})
            .with({pageView: 'form', data: undefined}, () => <Skeleton />)
            .with(
              {pageView: 'form', data: Pattern.not(undefined)},
              ({data}) => (
                <FormJS
                  schema={data.schema}
                  handleSubmit={async (variables) => {
                    await startExternalProcess({variables, bpmnProcessId});
                  }}
                  onImportError={() => {
                    setPageView('invalid-form-schema');
                  }}
                  onSubmitError={() => {
                    setPageView('failed-submission');
                  }}
                  onSubmitSuccess={() => {
                    setPageView('submit-success');
                  }}
                />
              ),
            )
            .with({pageView: 'submit-success'}, () => (
              <Message
                icon={{
                  altText: 'Success checkmark',
                  path: CheckImage,
                }}
                heading="Success!"
                description={
                  <>
                    Your form has been successfully submitted.
                    <br />
                    You can close this window now.
                  </>
                }
              />
            ))
            .with({pageView: 'form-not-found'}, () => (
              <Message
                icon={{
                  altText: 'Error robot',
                  path: ErrorRobotImage,
                }}
                heading="404 - Page not found"
                description="We're sorry! The requested URL you're looking for could not be found."
              />
            ))
            .with({pageView: 'failed-submission'}, () => (
              <Message
                icon={{
                  altText: 'Error robot',
                  path: ErrorRobotImage,
                }}
                heading="Something went wrong"
                description="Please try again later and reload the page."
                button={{
                  label: 'Reload',
                  onClick: () => {
                    reset();
                  },
                }}
              />
            ))
            .with({pageView: 'invalid-form-schema'}, () => (
              <Message
                icon={{
                  altText: 'Error robot',
                  path: ErrorRobotImage,
                }}
                heading="Invalid form"
                description="Something went wrong and the form could not be displayed. Please contact your provider."
              />
            ))
            .exhaustive()}
        </FormContainer>
      </Content>
    </>
  );
};

StartProcessFromForm.displayName = 'StartProcessFromForm';

export {StartProcessFromForm as Component};
