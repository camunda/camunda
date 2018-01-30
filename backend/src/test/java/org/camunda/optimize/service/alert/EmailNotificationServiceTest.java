package org.camunda.optimize.service.alert;

import com.icegreen.greenmail.util.DummySSLSocketFactory;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.mail.internet.MimeMessage;
import java.security.Security;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/unit/applicationContext.xml"})
public class EmailNotificationServiceTest {

  @Autowired
  @Mock
  private ConfigurationService configurationService;

  @Autowired
  private EmailNotificationService notificationService;

  private GreenMail greenMail;

  @After
  public void cleanUp() {
    if (greenMail != null) {
      greenMail.stop();
    }
  }

  private void initGreenMail(int port, String protocol) {
    greenMail = new GreenMail(new ServerSetup(port, null, protocol));
    greenMail.start();
    greenMail.setUser("from@localhost.com", "demo", "demo");
    greenMail.setUser("to@localhost.com", "demo", "demo");
  }

  @Test
  public void sendEmailWithoutSecureConnection() {

    // given
    mockConfig("demo", "demo","from@localhost.com", "127.0.0.1", 6666, "NONE");
    initGreenMail(6666, ServerSetup.PROTOCOL_SMTP);

    // when
    notificationService.notifyRecipient("some body text", "to@localhost.com");

    // then
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails.length, is(1));
    assertThat(GreenMailUtil.getBody(emails[0]), is("some body text"));
  }

  @Test
  public void sendEmailWithSSLTLSProtocol() {
    // given
    Security.setProperty("ssl.SocketFactory.provider", DummySSLSocketFactory.class.getName());
    mockConfig("demo", "demo","from@localhost.com", "127.0.0.1", 5555, "SSL/TLS");
    initGreenMail(5555, ServerSetup.PROTOCOL_SMTPS);

    // when
    notificationService.notifyRecipient("some body text", "to@localhost.com");

    // then
    MimeMessage[] emails = greenMail.getReceivedMessages();
    assertThat(emails.length, is(1));
    assertThat(GreenMailUtil.getBody(emails[0]), is("some body text"));
  }

  private void mockConfig(String username, String password, String address, String hostname, int port, String protocol) {

    configurationService.setAlertEmailUsername(username);
    configurationService.setAlertEmailPassword(password);
    configurationService.setAlertEmailAddress(address);
    configurationService.setAlertEmailHostname(hostname);
    configurationService.setAlertEmailPort(port);
    configurationService.setAlertEmailProtocol(protocol);

  }


}
