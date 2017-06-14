import {jsx, Children, createReferenceComponent, addClass, removeClass, setElementVisibility} from 'view-utils';
import {readFile} from 'utils/readFiles';
import {Header} from 'main/header';
import {Footer} from 'main/footer';
import {Notifications, addNotification} from 'notifications';
import {get, post} from 'http';

export function License() {
  return (node, eventsBus) => {
    const Reference = createReferenceComponent();

    const template = <Children>
        <Header />
        <div className="site-wrap">
          <div className="page-wrap">
            <div style="margin: 2% 15%">
              <div className="alert" role="alert">
                <Reference name="message" />
              </div>
              <form>
                <Reference name="form" />
                <div className="form-group">
                  <label>
                    License Key
                  </label>
                  <textarea class="form-control" rows="12" placeholder="Please enter license key">
                    <Reference name="key" />
                  </textarea>
                  <input class="form-control" type="file">
                    <Reference name="file" />
                  </input>
                </div>
                <button type="submit" class="btn btn-default">Submit</button>
              </form>
            </div>
          </div>
        </div>
        <Notifications selector="notifications" />
        <Footer selector="footer" />
    </Children>;

    const templateUpdate = template(node, eventsBus);

    const fileNode = Reference.getNode('file');
    const keyNode = Reference.getNode('key');
    const formNode = Reference.getNode('form');
    const messageNode = Reference.getNode('message');

    setElementVisibility(messageNode, false);

    get('/api/license/validate')
      .then(response => response.json())
      .then(displaySucccess)
      .catch(response => response.json().then(displayError));

    fileNode.addEventListener('change', () => {
      readFile(fileNode.files[0])
        .then(({content}) => {
          keyNode.value = content;
          fileNode.value = null; // clears file input
        })
        .catch(error => {
          addNotification({
            status: 'Could not read file',
            text: error.toString(),
            isError: true
          });
        });
    });

    formNode.addEventListener('submit', event => {
      const key = keyNode.value;

      event.preventDefault();

      post('/api/license/validate-and-store', key, {
        headers: {
          'Content-Type': 'text/plain'
        }
      })
      .then(response => response.json())
      .then(displaySucccess)
      .catch(error => {
        return error
          .json()
          .then(displayError);
      });
    });

    function displayError({errorMessage}) {
      setElementVisibility(messageNode, true);

      messageNode.innerText = errorMessage;

      removeClass(messageNode, 'alert-success');
      addClass(messageNode, 'alert-danger');
    }

    function displaySucccess({customerId, validUntil, unlimited}) {
      let message = `Licensed for ${customerId}. `;

      if (!unlimited) {
        const date = new Date(validUntil);

        message += `Valid until ${date.getFullYear()}-${date.getMonth() + 1}-${date.getDate()}`;
      }

      setElementVisibility(messageNode, true);

      messageNode.innerText = message;

      removeClass(messageNode, 'alert-danger');
      addClass(messageNode, 'alert-success');
    }

    return templateUpdate;
  };
}
