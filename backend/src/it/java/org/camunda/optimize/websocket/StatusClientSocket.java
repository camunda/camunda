package org.camunda.optimize.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.status.StatusWithProgressDto;

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Client class to test Web Socket implementation of status
 * report is working. This class will assert 2 properties:
 *
 * 1. progress is report and not 0
 * 2. more then one message is received
 *
 * @author Askar Akhmerov
 */
@ClientEndpoint
public class StatusClientSocket {
  private int messagesCount;

  @OnMessage
  public void onText(String message, Session session) throws Exception {
    System.out.println("Message received from server:" + message);
    ObjectMapper objectMapper = new ObjectMapper();
    StatusWithProgressDto dto = objectMapper.readValue(message, StatusWithProgressDto.class);
    assertThat(dto.getProgress(), is(not(0L)));

    messagesCount = messagesCount + 1;

    if (messagesCount > 2) {
      session.close();
    }
  }

}
