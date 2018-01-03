package org.camunda.optimize.service.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.VariableFilterDataDto;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.CoreMatchers.containsString;
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
  public void testFilterSerialization() throws Exception {
    ReportDataDto data = underTest.readValue(this.getClass().getResourceAsStream("/test/data/filter_request.json"), ReportDataDto.class);
    assertThat(((VariableFilterDataDto)data.getFilter().get(0).getData()).getValues().get(0), is("true"));

    data = underTest.readValue(this.getClass().getResourceAsStream("/test/data/filter_request_single.json"), ReportDataDto.class);
    assertThat(((VariableFilterDataDto)data.getFilter().get(0).getData()).getValues().get(0), is("true"));
  }

  @Test
  public void testDateSerialization() throws Exception {
    DateHolder instance = new DateHolder();
    instance.setDate(OffsetDateTime.now());
    String s = underTest.writeValueAsString(instance);
    assertThat(s, is(notNullValue()));

    DateHolder result = underTest.readValue(
        s,
        DateHolder.class);
    assertThat(result.getDate(), is(instance.getDate()));
  }

  @Test
  public void testFromString() throws JsonProcessingException {
    String value = "2017-12-11T17:28:38.222+0100";
    DateHolder toTest = new DateHolder();
    toTest.setDate(OffsetDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")));

    assertThat("value is [" + value + "]", underTest.writeValueAsString(toTest), containsString(value));
  }

  @Test
  public void testFromStringWithOldEngineFormat() throws JsonProcessingException {
    String value = "2017-12-11T17:28:38";
    DateHolder toTest = new DateHolder();
    toTest.setDate(
        ZonedDateTime
            .parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneId.systemDefault()))
            .toOffsetDateTime()
    );

    assertThat("value is [" + value + "]", underTest.writeValueAsString(toTest), containsString(value));
  }

  static class DateHolder {
    private OffsetDateTime date;

    public OffsetDateTime getDate() {
      return date;
    }

    public void setDate(OffsetDateTime date) {
      this.date = date;
    }
  }
}