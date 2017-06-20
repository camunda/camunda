import {
  jsx, Children, createReferenceComponent, addClass,
  removeClass, setElementVisibility, $window
} from 'view-utils';
import {Header} from 'main/header';
import {Footer} from 'main/footer';
import {Notifications} from 'notifications';
import {checkLicense, uploadLicense} from './service';
import {formatDate} from 'utils';

// Given in seconds
const redirectionTimeout = 10;

export function License() {
  return (node, eventsBus) => {
    const Reference = createReferenceComponent();

    const template = <Children>
        <Header redirect="true" />
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

    const keyNode = Reference.getNode('key');
    const formNode = Reference.getNode('form');
    const messageNode = Reference.getNode('message');

    setElementVisibility(messageNode, false);

    checkLicense()
      .then(displaySucccess)
      .catch(response => response.json().then(displayError));

    formNode.addEventListener('submit', event => {
      const key = keyNode.value;

      event.preventDefault();

      uploadLicense(key)
        .then(licenseData => {
          displaySucccess(licenseData, true);

          $window.setTimeout(() => {
            $window.location.pathname = '/';
          }, redirectionTimeout * 1000);
        })
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

    function displaySucccess({customerId, validUntil, unlimited}, redirection) {
      let message = `Licensed for ${customerId}.`;

      if (!unlimited) {
        const date = new Date(validUntil);

        message += ` Valid until ${formatDate(date)}.`;
      }

      if (redirection) {
        message += ` You will be redirected to login page shortly.
          <a href="/">Click here to go to login page immediately</a>
        `;
      }

      setElementVisibility(messageNode, true);

      messageNode.innerHTML = message;

      removeClass(messageNode, 'alert-danger');
      addClass(messageNode, 'alert-success');
    }

    return templateUpdate;
  };
}
