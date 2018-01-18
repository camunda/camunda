package org.camunda.optimize.service.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * @author Askar Akhmerov
 */
@Component
public class EmailNotificationService implements NotificationService {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Override
  public void notifyRecipient(String text, String destination) {
    logger.debug("sending email [{}] to [{}]", text, destination);
  }
}
