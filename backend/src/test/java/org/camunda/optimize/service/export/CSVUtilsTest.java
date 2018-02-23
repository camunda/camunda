package org.camunda.optimize.service.export;

import org.camunda.optimize.dto.optimize.query.report.result.raw.RawDataProcessInstanceDto;
import org.junit.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

/**
 * @author Askar Akhmerov
 */
public class CSVUtilsTest {

  @Test
  public void testMapping() {
    //given
    List<RawDataProcessInstanceDto> toMap = RawDataHelper.getRawDataProcessInstanceDtos();

    //when
    List<String[]> result = CSVUtils.map(toMap);

    //then
    assertThat(result.size(), is(4));
    assertThat(result.get(0).length, is(8));
  }

}