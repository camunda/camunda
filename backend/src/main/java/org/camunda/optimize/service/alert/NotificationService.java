package org.camunda.optimize.service.alert;

import org.springframework.stereotype.Component;

/**
 * @author Askar Akhmerov
 */
public interface NotificationService {

  void notifyRecipient(String text, String destination);
}
