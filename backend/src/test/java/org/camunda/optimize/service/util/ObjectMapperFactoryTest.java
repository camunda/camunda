package org.camunda.optimize.service.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/unit/applicationContext.xml"})
public class ObjectMapperFactoryTest {

  @Autowired
  private ObjectMapper underTest;

  @Test
  public void testDateSerialization() throws Exception{
    DateHolder instance = new DateHolder();
    instance.setDate(LocalDateTime.now());
    String s = underTest.writeValueAsString(instance);
    assertThat(s, is(notNullValue()));

    DateHolder result = underTest.readValue(
        s,
        DateHolder.class);
    assertThat(result.getDate(), is(instance.getDate().truncatedTo(ChronoUnit.SECONDS)));
  }

  static class DateHolder {
    private LocalDateTime date;

    public LocalDateTime getDate() {
      return date;
    }

    public void setDate(LocalDateTime date) {
      this.date = date;
    }
  }
}